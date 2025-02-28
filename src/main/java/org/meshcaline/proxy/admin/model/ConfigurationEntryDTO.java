/*
 * This file is part of the *meshcaline proxy* project.
 *
 * Copyright (C) 2025, Andreas Schmidt
 *
 * *meshcaline proxy* is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
