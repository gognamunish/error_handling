package com.cfbl.platform.core.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for platform services and integration settings.
 */
@ConfigurationProperties(prefix = "kxt.platform")
public class PlatformProperties {

    private boolean exposeEndpointInErrors = true;
    private Map<String, ServiceDefinition> services = new LinkedHashMap<>();

    public boolean isExposeEndpointInErrors() {
        return exposeEndpointInErrors;
    }

    public void setExposeEndpointInErrors(boolean exposeEndpointInErrors) {
        this.exposeEndpointInErrors = exposeEndpointInErrors;
    }

    public Map<String, ServiceDefinition> getServices() {
        return services;
    }

    public void setServices(Map<String, ServiceDefinition> services) {
        this.services = services;
    }

    /**
     * Service-level endpoint and protocol metadata.
     */
    public static class ServiceDefinition {

        private String endpointUrl;
        private String wsdlUrl;
        private String jdbcUrl;
        private String openapiVersion;
        private String wsdlVersion;
        private String soapAction;
        private String portName;
        private String schema;
        private String catalog;

        public String getEndpointUrl() {
            return endpointUrl;
        }

        public void setEndpointUrl(String endpointUrl) {
            this.endpointUrl = endpointUrl;
        }

        public String getWsdlUrl() {
            return wsdlUrl;
        }

        public void setWsdlUrl(String wsdlUrl) {
            this.wsdlUrl = wsdlUrl;
        }

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public void setJdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
        }

        public String getOpenapiVersion() {
            return openapiVersion;
        }

        public void setOpenapiVersion(String openapiVersion) {
            this.openapiVersion = openapiVersion;
        }

        public String getWsdlVersion() {
            return wsdlVersion;
        }

        public void setWsdlVersion(String wsdlVersion) {
            this.wsdlVersion = wsdlVersion;
        }

        public String getSoapAction() {
            return soapAction;
        }

        public void setSoapAction(String soapAction) {
            this.soapAction = soapAction;
        }

        public String getPortName() {
            return portName;
        }

        public void setPortName(String portName) {
            this.portName = portName;
        }

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }

        public String getCatalog() {
            return catalog;
        }

        public void setCatalog(String catalog) {
            this.catalog = catalog;
        }
    }
}
