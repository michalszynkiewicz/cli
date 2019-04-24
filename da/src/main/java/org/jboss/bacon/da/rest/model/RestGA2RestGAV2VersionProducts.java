package org.jboss.bacon.da.rest.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class RestGA2RestGAV2VersionProducts   {
  
  private @Valid String groupId;
  private @Valid String artifactId;
  private @Valid List<RestGAV2VersionProducts> gavProducts = new ArrayList<RestGAV2VersionProducts>();

  /**
   **/
  public RestGA2RestGAV2VersionProducts groupId(String groupId) {
    this.groupId = groupId;
    return this;
  }

  
  @ApiModelProperty(value = "")
  @JsonProperty("groupId")
  public String getGroupId() {
    return groupId;
  }
  public void setGroupId(String groupId) {
    this.groupId = groupId;
  }

  /**
   **/
  public RestGA2RestGAV2VersionProducts artifactId(String artifactId) {
    this.artifactId = artifactId;
    return this;
  }

  
  @ApiModelProperty(value = "")
  @JsonProperty("artifactId")
  public String getArtifactId() {
    return artifactId;
  }
  public void setArtifactId(String artifactId) {
    this.artifactId = artifactId;
  }

  /**
   **/
  public RestGA2RestGAV2VersionProducts gavProducts(List<RestGAV2VersionProducts> gavProducts) {
    this.gavProducts = gavProducts;
    return this;
  }

  
  @ApiModelProperty(value = "")
  @JsonProperty("gavProducts")
  public List<RestGAV2VersionProducts> getGavProducts() {
    return gavProducts;
  }
  public void setGavProducts(List<RestGAV2VersionProducts> gavProducts) {
    this.gavProducts = gavProducts;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RestGA2RestGAV2VersionProducts restGA2RestGAV2VersionProducts = (RestGA2RestGAV2VersionProducts) o;
    return Objects.equals(groupId, restGA2RestGAV2VersionProducts.groupId) &&
        Objects.equals(artifactId, restGA2RestGAV2VersionProducts.artifactId) &&
        Objects.equals(gavProducts, restGA2RestGAV2VersionProducts.gavProducts);
  }

  @Override
  public int hashCode() {
    return Objects.hash(groupId, artifactId, gavProducts);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class RestGA2RestGAV2VersionProducts {\n");
    
    sb.append("    groupId: ").append(toIndentedString(groupId)).append("\n");
    sb.append("    artifactId: ").append(toIndentedString(artifactId)).append("\n");
    sb.append("    gavProducts: ").append(toIndentedString(gavProducts)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

