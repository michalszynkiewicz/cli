package org.jboss.pnc.bacon.pig.impl.repo;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.jboss.pnc.bacon.pig.impl.PigContext;
import org.jboss.pnc.bacon.pig.impl.config.Flow;
import org.jboss.pnc.bacon.pig.impl.config.PigConfiguration;
import org.jboss.pnc.bacon.pig.impl.config.ProductConfig;
import org.jboss.pnc.bacon.pig.impl.config.RepoGenerationData;
import org.jboss.pnc.bacon.pig.impl.config.RepoGenerationStrategy;
import org.jboss.pnc.bacon.pig.impl.documents.Deliverables;
import org.jboss.pnc.bacon.pig.impl.pnc.BuildInfoCollector;
import org.jboss.pnc.bacon.pig.impl.pnc.PncBuild;
import org.jboss.pnc.bacon.pig.impl.utils.FileUtils;
import org.jboss.pnc.bacon.pig.impl.utils.ResourceUtils;
import org.jboss.pnc.bacon.pig.impl.utils.indy.Indy;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.mockito.Mockito.doReturn;

public class ResolveOnlyRepositoryTest {

    WireMockServer wireMockServer = new WireMockServer();

    @Test
    void resolveAndRepackageShouldGenerateRepository() {
/*        wireMockServer.addMockServiceRequestListener((request, response) -> {
            System.out.printf(
                    "-> [%s] %s: %s\n",
                    request.getMethod(),
                    request.getAbsoluteUrl(),
                    request.getBodyAsString());
            System.out.printf(
                    "<- [%s] --- \n%s\n%s\n",
                    response.getStatus(),
                    response.getBodyAsString(),
                    response.getHeaders());
        });*/
        wireMockServer.start();
        stubFor(
                get(urlEqualTo("/org/jboss/jboss-parent/37/jboss-parent-37.pom"))
                        .willReturn(aResponse().withBodyFile("sample.pom")));
        stubFor(
                get(urlMatching("/io/vertx/vertx-bridge-common/4.1.0/vertx-bridge-common-4.1.0.jar"))
                        .willReturn(aResponse().withBodyFile("sample.jar")));
        stubFor(
                get(urlMatching("/io/vertx/vertx-bridge-common/4.1.0/vertx-bridge-common-4.1.0.pom"))
                        .willReturn(aResponse().withBodyFile("sample.pom")));
        mockPigContextAndMethods();
        mockIndySettingsFile();

        PigConfiguration pigConfiguration = mockPigConfigurationAndMethods();

        RepoGenerationData generationDataSpy = mockRepoGenerationDataAndMethods();

        mockParamsAndMethods(generationDataSpy);

        mockFlowAndMethods(pigConfiguration, generationDataSpy);

        Map<String, PncBuild> buildsSpy = mockBuildsAndMethods(generationDataSpy);

        Path configurationDirectory = Mockito.mock(Path.class);

        mockResourceUtilsMethods(configurationDirectory);

        Deliverables deliverables = mockDeliverables(pigConfiguration);

        String releasePath = createReleasePath();

        BuildInfoCollector buildInfoCollectorMock = Mockito.mock(BuildInfoCollector.class);

        RepoManager repoManager = new RepoManager(
                pigConfiguration,
                releasePath,
                deliverables,
                buildsSpy,
                configurationDirectory,
                false,
                false,
                buildInfoCollectorMock);

        RepoManager repoManagerSpy = Mockito.spy(repoManager);

        prepareFakeExtensionArtifactList(repoManagerSpy);

        RepositoryData repoData = repoManagerSpy.prepare();

        assert repoData.getRepositoryPath().toString().equals("/tmp/resolveRepoTest/rh-sample-maven-repository.zip");

        Set<String> repoZipContents = repoZipContentList();

        repoData.getFiles().forEach((file) -> {
            String filePath = file.getAbsolutePath().replaceAll(".+/deliverable-generation\\d+/", "");
            if (!repoZipContents.contains(filePath)) {
                System.out.println("File not included " + filePath);
            }
            assert repoZipContents.contains(filePath);
        });
        wireMockServer.stop();
    }

    private void mockPigContextAndMethods() {
        PigContext pigContext = Mockito.mock(PigContext.class);
        doReturn(false).when(pigContext).isTempBuild();
        MockedStatic<PigContext> pigContextMockedStatic = Mockito.mockStatic(PigContext.class);
        pigContextMockedStatic.when(PigContext::get).thenReturn(pigContext);
    }

    private void mockIndySettingsFile() {
        String pathToTestSettingsFile = ResourceUtils.extractToTmpFile("/indy-settings.xml", "settings", ".xml")
                .getAbsolutePath();
        FileUtils
                .replaceFileString("https://repo1.maven.org/maven2/", "http://localhost:8080/", pathToTestSettingsFile);
        MockedStatic<Indy> indyMockedStatic = Mockito.mockStatic(Indy.class);
        indyMockedStatic.when(() -> Indy.getConfiguredIndySettingsXmlPath(false)).thenReturn(pathToTestSettingsFile);
    }

    private PigConfiguration mockPigConfigurationAndMethods() {
        PigConfiguration pigConfiguration = Mockito.mock(PigConfiguration.class, Mockito.RETURNS_DEEP_STUBS);
        ProductConfig productConfig = Mockito.mock(ProductConfig.class);
        doReturn(productConfig).when(pigConfiguration).getProduct();
        doReturn("sample").when(productConfig).getName();
        return pigConfiguration;
    }

    private RepoGenerationData mockRepoGenerationDataAndMethods() {
        RepoGenerationData generationData = new RepoGenerationData();
        RepoGenerationData generationDataSpy = Mockito.spy(generationData);
        generationDataSpy.setStrategy(RepoGenerationStrategy.RESOLVE_ONLY);

        return generationDataSpy;
    }

    private void mockParamsAndMethods(RepoGenerationData generationData) {
        Map<String, String> params = new HashMap<>();
        params.put("extensionsListUrl", "http://gitlab.cee.com");
        doReturn(params).when(generationData).getParameters();
    }

    private void mockFlowAndMethods(PigConfiguration pigConfiguration, RepoGenerationData generationData) {
        Flow mockFlow = Mockito.mock(Flow.class);
        doReturn(generationData).when(mockFlow).getRepositoryGeneration();
        doReturn(mockFlow).when(pigConfiguration).getFlow();
    }

    private Map<String, PncBuild> mockBuildsAndMethods(RepoGenerationData generationData) {
        Map<String, PncBuild> builds = new HashMap<>();
        Map<String, PncBuild> buildsSpy = Mockito.spy(builds);

        PncBuild pncBuild = Mockito.mock(PncBuild.class);
        buildsSpy.put(generationData.getSourceBuild(), pncBuild);

        return buildsSpy;
    }

    private void mockResourceUtilsMethods(Path configurationDirectory) {
        MockedStatic<ResourceUtils> resourceUtilsMockedStatic = Mockito.mockStatic(ResourceUtils.class);
        resourceUtilsMockedStatic.when(
                () -> ResourceUtils.getOverridableResource("/repository-example-settings.xml", configurationDirectory))
                .thenReturn("fake-resource-name");

        resourceUtilsMockedStatic
                .when(() -> ResourceUtils.getOverridableResource("/repository-README.md", configurationDirectory))
                .thenReturn("fake-resource-readme");
    }

    private Deliverables mockDeliverables(PigConfiguration pigConfiguration) {
        Deliverables deliverables = Mockito.mock(Deliverables.class);
        doReturn("rh-sample-maven-repository.zip").when(deliverables).getRepositoryZipName();
        doReturn("rh-sample-").when(pigConfiguration).getTopLevelDirectoryPrefix();
        return deliverables;
    }

    private String createReleasePath() {
        String releasePath = "/tmp/resolveRepoTest/";
        File targetRepoZipPath = new File(releasePath);
        targetRepoZipPath.mkdirs();
        return releasePath;
    }

    private void prepareFakeExtensionArtifactList(RepoManager repoManager) {
        Artifact vertxWeb = new DefaultArtifact("io.vertx", "vertx-bridge-common", "jar", "4.1.0");

        List<Artifact> extensionsArtifactList = new ArrayList<>();
        extensionsArtifactList.add(vertxWeb);
        doReturn(extensionsArtifactList).when(repoManager).parseExtensionsArtifactList("http://gitlab.cee.com");
        Map<Artifact, String> redhatVersionMap = new HashMap<>();
        redhatVersionMap.put(vertxWeb, "4.1.0");
        doReturn(redhatVersionMap).when(repoManager).collectRedhatVersions(extensionsArtifactList);
    }

    private Set<String> repoZipContentList() {
        ClassLoader classLoader = getClass().getClassLoader();
        File repoZipContentListFile = new File(
                Objects.requireNonNull(classLoader.getResource("resolve-and-repackage-repo-artifact-list.txt"))
                        .getFile());
        Set<String> repoZipContents = new HashSet<>();

        try (BufferedReader br = new BufferedReader(new FileReader(repoZipContentListFile))) {
            for (String line; (line = br.readLine()) != null;) {
                repoZipContents.add(line);
            }
            return repoZipContents;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return Collections.emptySet();
    }
}
