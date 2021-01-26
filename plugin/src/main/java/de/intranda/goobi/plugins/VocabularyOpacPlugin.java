package de.intranda.goobi.plugins;

import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringUtils;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IOpacPlugin;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.HttpClientHelper;
import de.unigoettingen.sub.search.opac.ConfigOpac;
import de.unigoettingen.sub.search.opac.ConfigOpacCatalogue;
import de.unigoettingen.sub.search.opac.ConfigOpacDoctype;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.Person;
import ugh.dl.Prefs;
import ugh.fileformats.mets.MetsMods;

@PluginImplementation
@Log4j
public class VocabularyOpacPlugin implements IOpacPlugin {

    @Getter
    private PluginType type = PluginType.Opac;

    @Getter
    private String title = "intranda_opac_vocabulary";

    @Getter
    private String gattung;
    @Getter @Setter
    private String atstsl;
    @Getter
    private int hitcount = 0;
    private String url;
    private String suffix;
    
    /**
     * method to read in configuration parameters from config file
     * 
     * This implementation is just for demo purposes how to read lists of values
     * from the sample configuration file
     */
    private void loadConfiguration() {
        XMLConfiguration config = ConfigPlugins.getPluginConfig(title);
        config.setExpressionEngine(new XPathExpressionEngine());
        
        // read a simple configuration value
        String configValue = config.getString("value", "emtpy value");
        
        // read a list of sub entries
        List<HierarchicalConfiguration> docstructList = config.configurationsAt("/docstructs/docstruct");
        if (docstructList != null) {
            for (HierarchicalConfiguration docstruct : docstructList) {
                String xmlName = docstruct.getString("@name");
                String rulesetName = docstruct.getString("@rulesetName");
            }
        }
        
        // read some sample sub entry from config file
        HierarchicalConfiguration query = config.configurationAt("/docstructs/query");
        if (query != null) {
            url = query.getString("@url");
            suffix = query.getString("@suffix");
        }

        // read another list of sub entries
        List<HierarchicalConfiguration> mappings = config.configurationsAt("/mapping/element");
        if (mappings != null) {
            for (HierarchicalConfiguration m : mappings) {
                String label = m.getString("@label");
                String value = m.getString("@value");
            }
        }
    }

    /**
     * method to request the source system to request metadata from there and to return 
     * a Fileformat as result with mapped metadata
     * 
     * @param field the field to use for searching
     * @param term the term to search for
     * @param catalogueConfig the ConfigOpacCatalogue from Goobi with standard configuration
     * @prefs the preferences from the currently used ruleset
     * 
     * @return Fileformat which can be stored as METS file
     */
    @Override
    public Fileformat search(String field, String term, ConfigOpacCatalogue catalogueConfig, Prefs prefs) throws Exception {

        // first read the configuration
        loadConfiguration();

        // set the term to something specific for demo purposes
        term = "id=opac-de-b1532:ppn:680191259";
        
        // create a FileFormat
        Fileformat mm = null;
        if (StringUtils.isNotBlank(term)) {
            // read information from the catalogue configuration or use it from
            // the plugin configuration itself if wanted
            // String url = catalogueConfig.getAddress() + term;
            String completeUrl = url + term + suffix;            

            // try to request the URL and get the result as String
            String response = HttpClientHelper.getStringFromUrl(completeUrl);
            if (StringUtils.isBlank(response)) {
                hitcount = 0;
                log.debug("no record found using the url: " + completeUrl);
                return null;
            } 
            
            // if there was a result parse it now using the configuration to enrich the Fileformat
            hitcount = 1;
            mm = new MetsMods(prefs);
            DigitalDocument digitalDocument = new DigitalDocument();
            mm.setDigitalDocument(digitalDocument);
            DocStruct item = digitalDocument.createDocStruct(prefs.getDocStrctTypeByName("Monograph"));
            digitalDocument.setLogicalDocStruct(item);
            gattung = item.getType().getName();
            DocStruct physical = digitalDocument.createDocStruct(prefs.getDocStrctTypeByName("BoundBook"));
            digitalDocument.setPhysicalDocStruct(physical);
            
            Metadata mdTitle = new Metadata(prefs.getMetadataTypeByName("TitleDocMain"));
            mdTitle.setValue("my title");
            item.addMetadata(mdTitle);
            
            Metadata mdID = new Metadata(prefs.getMetadataTypeByName("CatalogIDDigital"));
            mdID.setValue("9999999999999");
            item.addMetadata(mdID);

            Person p = new Person(prefs.getMetadataTypeByName("Author"));
            p.setLastname("Peter");
            p.setFirstname("Pan");
            item.addPerson(p);                
        }
        return mm;
    }

    /* *************************************************************** */
    /*                                                                 */
    /* the following methods are mostly not needed for typical imports */
    /*                                                                 */
    /* *************************************************************** */

    @Override
    public ConfigOpacDoctype getOpacDocType() {
        return ConfigOpac.getInstance().getDoctypeByName(gattung);
    }

    @Override
    public String createAtstsl(String value, String value2) {
        return null;
    }
    
    @Data
    public class SearchField {
        private String id;

        // displayed label
        private String label;

        // type of the field, implemented are text, select and select+text
        private String type;

        // list of possible values
        private List<String> selectList;

        // value of the selected field
        private String selectedField;

        // entered text, gets filled with default value
        private String text;

        private String url;
    }

}