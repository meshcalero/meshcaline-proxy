package org.meshcaline.proxy.admin.model;

import org.meshcaline.proxy.configuration.model.ProxyConfiguration;

public interface ConfigurationEntryDTO {

    class In {
        private String ingressURIRegEx;
        private String egressURIReplace;

        public String getIngressURIRegEx() {
            return ingressURIRegEx;
        }

        public void setIngressURIRegEx(String ingressURIRegEx) {
            this.ingressURIRegEx = ingressURIRegEx;
        }

        public String getEgressURIReplace() {
            return egressURIReplace;
        }

        public void setEgressURIReplace(String egressURIReplace) {
            this.egressURIReplace = egressURIReplace;
        }

        private void from(ProxyConfiguration.Mapping entry){
            this.setIngressURIRegEx(entry.ingressURIRegEx());
            this.setEgressURIReplace(entry.getEgressURIReplace());
        }

        public ProxyConfiguration.Mapping in() {
            return new ProxyConfiguration.Mapping(this.getIngressURIRegEx(),this.getEgressURIReplace());
        }

        public ProxyConfiguration.Mapping in(String id) {
            return new ProxyConfiguration.Mapping(id, this.getIngressURIRegEx(),this.getEgressURIReplace());
        }

    }

    class Out extends In {
        private String id;
        // Getters and setters
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public static Out out(ProxyConfiguration.Mapping entry){
            final Out result = new Out();
            result.from(entry);
            return result;
        }

        private void from(ProxyConfiguration.Mapping entry){
            super.from(entry);
            setId(entry.id());
        }
    }

}
