package com.github.topi314.lavasrc.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.lavasrc.tidal")
@Component
public class TidalConfig {

  private String countryCode;
  private int searchLimit;
  

  public String getCountryCode() {
    return this.countryCode;
  }

  public void setCountryCode(String countryCode) {
    this.countryCode = countryCode;
  }

public int getSearchLimit() {
    return this.searchLimit;
}

  public void setSearchLimit(int searchLimit) {
    this.searchLimit = searchLimit;
  }
}
