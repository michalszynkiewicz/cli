package org.jboss.pnc.bacon.pig.pnc;

import com.fasterxml.jackson.core.type.TypeReference;
import org.jboss.pnc.bacon.common.exception.TodoException;
import org.jboss.pnc.bacon.pig.config.build.BuildConfig;
import org.jboss.pnc.bacon.pig.config.build.Config;
import org.jboss.pnc.bacon.pig.config.build.Product;
import org.jboss.pnc.bacon.pig.utils.CollectionUtils;
import org.jboss.pnc.dto.BuildConfiguration;
import org.jboss.pnc.dto.ProductMilestone;
import org.jboss.pnc.dto.ProductVersion;
import org.jboss.pnc.dto.Project;
import org.jboss.pnc.dto.SCMRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * mstodo: Header
 *
 * @author Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 * <br>
 * Date: 12/18/18
 */
public class PncConfigurer {
    private static final Logger log = LoggerFactory.getLogger(PncConfigurer.class);
    public static final String LIST_PRODUCTS = "list-products";

    private PncRestDao dao = new PncRestDao();

    private final Config config;
    private int productId;
    private int versionId;
    private int milestoneId;
    private int buildGroupId;
    private List<BuildConfigData> configs;

    public PncConfigurer(Config config) {
        this.config = config;
    }

    public PncImportResult performImport() {
        productId = getOrGenerateProduct();
        versionId = getOrGenerateVersion(productId);
        milestoneId = getOrGenerateMilestone(
                versionId,
                config.getMajorMinor(),
                pncMilestoneString(),
                config.getProduct().getIssueTrackerUrl()
        ).getId();
        dao.markMilestoneCurrent(versionId, milestoneId);
        buildGroupId = getOrGenerateBuildGroup();

        configs = getAddOrUpdateBuildConfigs();
        log.debug("Setting up build dependencies");
        setUpBuildDependencies();

        log.debug("Adding builds to group");
        addBuildConfigIdsToGroup();
        return new PncImportResult(milestoneId, buildGroupId, versionId, configs);
    }

    private ProductMilestone getOrGenerateMilestone(int versionId, String majorMinor, String pncMilestoneString, String issueTrackerUrl) {
        Optional<ProductMilestone> maybeMilestone = dao.getMilestoneIdForVersionAndName(versionId, pncMilestoneString);
        return maybeMilestone.orElseGet(
                () -> dao.createMilestone(versionId, majorMinor, pncMilestoneString)
        );
    }

    private void setUpBuildDependencies() {
        configs.parallelStream().forEach(this::setUpBuildDependencies);
    }

    private void setUpBuildDependencies(BuildConfigData config) {
        Integer id = config.getId();

        Set<Integer> dependencies =
                config.getDependencies()
                        .stream()
                        .map(this::getConfigIdByName)
                        .collect(Collectors.toSet());
        Set<Integer> currentDependencies = config.getOldConfig().getDependencyIds();

        Set<Integer> superfluous = CollectionUtils.subtractSet(currentDependencies, dependencies);
        if (!superfluous.isEmpty()) {
            superfluous.forEach(
                    dependencyId -> dao.removeBuildConfigDependency(id, dependencyId)
            );
        }

        Set<Integer> missing = CollectionUtils.subtractSet(dependencies, currentDependencies);
        if (!missing.isEmpty()) {
            missing.forEach(dependencyId -> dao.addBuildConfigDependency(id, dependencyId));
        }

        if (!superfluous.isEmpty() || !missing.isEmpty()) {
            config.setModified(true);
        }
    }

    private Integer getConfigIdByName(String name) {
        Optional<BuildConfigData> maybeConfig = configs.stream()
                .filter(c -> c.getName().equals(name))
                .findAny();
        return maybeConfig
                .orElseThrow(() -> new RuntimeException("Build config name " + name + " used to reference a dependency but no such build config defined"))
                .getId();
    }


    private void addBuildConfigIdsToGroup() {
        Collection<Integer> configIds =
                configs.stream()
                        .map(BuildConfigData::getId)
                        .collect(Collectors.toSet());
        dao.setBuildConfigurationsForGroup(buildGroupId, configIds);
    }

    private List<BuildConfigData> getAddOrUpdateBuildConfigs() {
        log.info("Adding/updating build configurations");
        List<BuildConfiguration> currentConfigs = getCurrentBuildConfigs(buildGroupId);
        dropConfigsFromInvalidVersion(currentConfigs, config.getBuilds(), versionId);
        return updateOrCreate(currentConfigs, config.getBuilds());
    }

    private List<BuildConfigData> updateOrCreate(
            List<BuildConfiguration> currentConfigs,
            List<BuildConfig> builds) {
        List<BuildConfigData> buildList = new ArrayList<>();
        for (BuildConfig bc : builds) {
            BuildConfigData data = new BuildConfigData(bc);
            for (BuildConfiguration config : currentConfigs) {
                if (config.getName().equals(bc.getName())) {
                    data.setOldConfig(config);
                    if (data.shouldBeUpdated()) {
                        updateBuildConfig(data);
                    }
                }
            }
            //Check if build exists already (globally)
            //True = Add to BCS and update BC (maybe ask?)
            Optional<BuildConfiguration> matchedBuildConfig = dao.getBuildConfigByName(bc.getName());

            if (matchedBuildConfig.isPresent()) {
                log.debug("Found matching build config for {}", bc.getName());
                data.setOldConfig(matchedBuildConfig.get());
                if (data.shouldBeUpdated()) {
                    updateBuildConfig(data);
                }
            } else {
                //False = Create new project/BC
                Integer configId = createBuildConfig(data.getNewConfig());
                data.setId(configId);
                log.debug("Didn't find matching build config for {}", bc.getName());
            }
            data.setModified(true);      // TODO: it looks cumbersome that each data is marked as modified
            buildList.add(data);
        }
        return buildList;
    }

    private Integer createBuildConfig(BuildConfig buildConfig) {
        Integer projectId = getOrGenerateProject(buildConfig.getProject());

        BuildConfiguration result;
        if (repositoryConfigExists(buildConfig) || buildConfig.getScmUrl() != null) {
            SCMRepository repo = getOrGenerateRepository(buildConfig);
            result = dao.createBuildConfiguration(buildConfig.toPncBuildConfig(projectId, Optional.of(repo.getId())));
        } else {
            result = dao.createBuildConfiguration(buildConfig.toPncBuildConfig(projectId, Optional.empty()));
        }
        return result.getId();
    }

    private Integer getOrGenerateProject(String projectName) {
        return dao.getProjectByName(projectName)
                .map(Project::getId)
                .orElseGet(() -> generateProject(projectName));
    }

//    private Integer createBuildConfigFromExternalUrl(Integer projectId, BuildConfig buildConfig) {
//        String createParams = buildConfig.toCreateParamsForExternalScm(projectId, versionId);
//        // TODO: simplify when NCL-3866 is fixed
//        String command = String.format("pnc create-build-configuration-process %s", createParams);
//        List<String> output = OSCommandExecutor.executor(command)
//                .redirectErrorStream(false)
//                .exec()
//                .getOut();
//        log.debug("configuration creation output: {}", StringUtils.join(output, "\n"));
//        log.debug("Due to NCL-3866, there's no way to tell if it succeeded, will wait for the configuration to be created");
//        return SleepUtils.waitFor(
//                () -> PncDao.invokeAndGetResultId("get-build-configuration -n " + buildConfig.getName(), 4),
//                10,
//                10 * 60,
//                true,
//                String.format("Timed out while waiting for build configuration %s to be created", buildConfig.getName())
//        );
//
//    }

    private boolean repositoryConfigExists(BuildConfig buildConfig) {
        return dao.getRepositoryConfigurationByScmUri(buildConfig.getShortScmURIPath()).isPresent(); //mstodo

    }


    private SCMRepository getOrGenerateRepository(BuildConfig buildConfig) {
        return dao.getRepositoryConfigurationForBuildConfigByScmUri(buildConfig.getShortScmURIPath())
                .orElseGet(() -> createRepository(buildConfig));
    }

    private SCMRepository createRepository(BuildConfig buildConfig) {
        return dao.createRepository(buildConfig.getScmRepository());
    }

    private Integer updateBuildConfig(BuildConfigData data) {
        Integer configId = data.getId();
        Integer projectId = getOrGenerateProject(data.getProject());

        // mstodo
        String updateParams = data.getNewConfig().toUpdateParams(projectId, data.getOldConfig(), versionId);
        log.info("Updating build configuration {}", data.getName());
        throw new TodoException();
//        PncDao.invoke(format("update-build-configuration %d %s", configId, updateParams));
//        return configId;
    }

//    private Integer getOrGenerateProject(String projectName) {
//        return getOrGenerate("list-projects -q name==" + projectName,
//                any -> true,
//                () -> generateProject(projectName)
//        );
//    }

    private Integer generateProject(String projectName) {
//        String command = format("create-project \"%s\"", projectName);
//        return PncDao.invokeAndGetResultId(command);
        throw new TodoException();
    }

    private List<BuildConfiguration> dropConfigsFromInvalidVersion(
            List<BuildConfiguration> currentConfigs,
            List<BuildConfig> newConfigs,
            int versionId) {
        Map<String, BuildConfig> newConfigsByName = BuildConfig.mapByName(newConfigs);
        List<BuildConfiguration> configsToDrop = currentConfigs.stream()
                .filter(config -> shouldBeDropped(config, versionId, newConfigsByName))
                .collect(Collectors.toList());
        if (!configsToDrop.isEmpty()) {
            throw new RuntimeException("The following configurations should be dropped or updated " +
                    "in an unsupported fashion, please drop or update them via PNC UI: " + configsToDrop +
                    ". Look above for the cause");
        }
        return configsToDrop;
    }

    private boolean shouldBeDropped(BuildConfiguration oldConfig,
                                    int versionId,
                                    Map<String, BuildConfig> newConfigsByName) {
//        String name = oldConfig.getName();
//        BuildConfig newConfig = newConfigsByName.get(name);
//        boolean configMismatch = oldConfig.getVersionId() == null || oldConfig.getVersionId() != versionId;
//        if (configMismatch) {
//            log.warn("Product version in the old config is different than the one in the new config for config {}", name);
//        }
//        return configMismatch || newConfig == null || !oldConfig.isUpgradableTo(newConfig);
        throw new TodoException();
    }


    private List<BuildConfiguration> getCurrentBuildConfigs(int buildGroupId) {
//        String command = format("list-build-configurations-for-set -i %d", buildGroupId);
//        List<String> output = PncDao.invoke(command, 4);
//        return PncCliParser.parseList(output, new TypeReference<List<BuildConfiguration>>() {
//        });
        throw new TodoException();
    }

    private int getOrGenerateBuildGroup() {
        Optional<Integer> buildConfigSetId = getBuildGroup();
        return buildConfigSetId.orElseGet(() -> generateBuildGroup(versionId));
    }

    private Optional<Integer> getBuildGroup() {
//        Optional<Integer> buildConfigSetId;
//        try {
//            buildConfigSetId = Optional.of(
//                    PncDao.invokeAndGetResultId(format("get-build-configuration-set --name \"%s\"", config.getGroup()), 4)
//            );
//        } catch (OSCommandException e) {
//            log.info(format("Product build group does not exist: {}, we'll create one", config.getGroup(), e));
//            buildConfigSetId = Optional.empty();
//        }
//        return buildConfigSetId;
        throw new TodoException();
    }

    private int getOrGenerateVersion(int productId) {
//        String command = versionsForProduct(productId);
//        return getOrGenerate(
//                command,
//                versionByMajorMinor(),
//                () -> generateVersion(productId)
//        );
        throw new TodoException();
    }

    private String versionsForProduct(int productId) {
        return format("list-versions-for-product -i %d", productId);
    }

    private Predicate<Map<String, ?>> versionByMajorMinor() {
        String version = config.getMajorMinor();
        return entry -> entry.get("version").equals(version);
    }

    private int getOrGenerateProduct() {
//        return getOrGenerate(
//                LIST_PRODUCTS,
//                productByName(),
//                this::generateProduct
//        );
        throw new TodoException();
    }

    private Predicate<Map<String, ?>> productByName() {
        String productName = config.getProduct().getName();
        return product -> product.get("name").equals(productName);
    }


    public PncImportResult readCurrentPncEntities() {
//        productId = getProduct();
//        versionId = getVersion();
//        Optional<Integer> maybeMilestone = dao.getMilestoneIdForVersionAndName(versionId, pncMilestoneString());
//        milestoneId = maybeMilestone
//                .orElseThrow(() -> new RuntimeException("Unable to find milestone " + pncMilestoneString())); // mstodo
//
//        buildGroupId = getBuildGroup()
//                .orElseThrow(() -> new RuntimeException("Unable to find build group " + config.getGroup()));
//
//        configs = getBuildConfigs();
//
//        return new PncImportResult(milestoneId, buildGroupId, versionId, configs);
        throw new TodoException();
    }

    private Integer generateProduct() {
        Product product = config.getProduct();
        return dao.createProduct(product).getId();
    }

    private Integer generateVersion(Integer productId) {
        String version = config.getMajorMinor();
        return dao.createProductVersion(productId, version).getId();
    }

    private Integer generateBuildGroup(Integer versionId) {
        String group = config.getGroup();
        return dao.createBuildConfigGroup(versionId, group).getId();
    }

    private List<BuildConfigData> getBuildConfigs() {
        List<BuildConfiguration> configs = getCurrentBuildConfigs(buildGroupId);

        return configs.stream()
                .map(config -> {
                    BuildConfigData result = new BuildConfigData(null);
                    result.setOldConfig(config);
                    return result;
                }).collect(Collectors.toList());
    }

    private int getVersion() {

        Optional<ProductVersion> maybeVersion = dao.getProductVersion(productId, config.getMajorMinor());
        return maybeVersion.map(ProductVersion::getId)
                .orElseThrow(() -> new RuntimeException("Unable to find version " + config.getMajorMinor() + " for product " + productId));
    }

    private Integer getProduct() {
        Optional<org.jboss.pnc.dto.Product> maybeProduct = dao.getProductByName(config.getProduct().getName());
        return maybeProduct.map(org.jboss.pnc.dto.Product::getId)
                .orElseThrow(() -> new RuntimeException("Unable to find product called " + config.getProduct().getName()));
    }

    private String pncMilestoneString() {
        return config.getMicro() + "." + config.getMilestone();
    }
}
