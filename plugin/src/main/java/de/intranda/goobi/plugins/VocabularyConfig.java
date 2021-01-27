package de.intranda.goobi.plugins;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.goobi.production.cli.helper.StringPair;

import lombok.Data;

@Data
public class VocabularyConfig {

    // use the default type if no other value is found
    private String defaultPublicationType;

    // take publication type from this record field
    private String publicationTypeField;

    // metadata name / record field name
    private List<StringPair> metadataMapping = new ArrayList<>();

    /**
     * loads the &lt;config&gt; block from xml file
     * 
     * @param xmlConfig
     */

    public VocabularyConfig(SubnodeConfiguration xmlConfig) {

        defaultPublicationType = xmlConfig.getString("/defaultPublicationType", null);

        publicationTypeField = xmlConfig.getString("/publicationType/@field", null);

        List<HierarchicalConfiguration> metadataConfiguration = xmlConfig.configurationsAt("/metadata");
        for (HierarchicalConfiguration mc : metadataConfiguration) {
            String metadataName = mc.getString("@name");
            String vocabularyField = mc.getString("@field");
            StringPair sp = new StringPair(metadataName, vocabularyField);
            metadataMapping.add(sp);
        }
    }
}
