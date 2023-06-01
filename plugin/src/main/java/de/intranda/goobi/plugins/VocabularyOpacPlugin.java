package de.intranda.goobi.plugins;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringUtils;
import org.goobi.production.cli.helper.StringPair;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IOpacPlugin;
import org.goobi.vocabulary.Field;
import org.goobi.vocabulary.VocabRecord;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.persistence.managers.VocabularyManager;
import de.unigoettingen.sub.search.opac.ConfigOpac;
import de.unigoettingen.sub.search.opac.ConfigOpacCatalogue;
import de.unigoettingen.sub.search.opac.ConfigOpacDoctype;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
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
    @Getter
    @Setter
    private String atstsl;
    @Getter
    private int hitcount = 0;

    protected ConfigOpacCatalogue coc;
    private VocabularyConfig config = null;
    @Setter
    private String opacName;

    @Setter
    @Getter
    private String workflowTitle;

    /**
     * method to request the source system to request metadata from there and to return a Fileformat as result with mapped metadata
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
        this.coc = catalogueConfig;
        String database = coc.getDatabase();
        // first read the configuration
        if (config == null) {
            config = getConfig(database);
        }

        List<VocabRecord> results = VocabularyManager.findExactRecords(database, term, field);

        if (results.isEmpty()) {
            // no records found
            hitcount = 0;
            return null;
        }
        hitcount = 1;
        VocabRecord rec = results.get(0);

        List<Field> fields = rec.getFields();
        String docStructType = config.getDefaultPublicationType();
        if (StringUtils.isNotBlank(config.getPublicationTypeField())) {
            for (Field f : fields) {
                if (f.getDefinition().getLabel().equals(config.getPublicationTypeField())) {
                    docStructType = f.getValue();
                }
            }
        }

        // create a FileFormat
        Fileformat mm = new MetsMods(prefs);
        DigitalDocument digitalDocument = new DigitalDocument();
        mm.setDigitalDocument(digitalDocument);
        DocStruct item = digitalDocument.createDocStruct(prefs.getDocStrctTypeByName(docStructType));
        digitalDocument.setLogicalDocStruct(item);
        gattung = item.getType().getName();
        DocStruct physical = digitalDocument.createDocStruct(prefs.getDocStrctTypeByName("BoundBook"));
        digitalDocument.setPhysicalDocStruct(physical);

        Metadata pathimagefiles = new Metadata(prefs.getMetadataTypeByName("pathimagefiles"));
        pathimagefiles.setValue("/images");
        physical.addMetadata(pathimagefiles);

        Metadata mdID = new Metadata(prefs.getMetadataTypeByName("CatalogIDDigital"));
        mdID.setValue(String.valueOf(rec.getId()));
        item.addMetadata(mdID);

        for (StringPair sp : config.getMetadataMapping()) {
            for (Field f : fields) {
                if (f.getDefinition().getLabel().equals(sp.getTwo())) {
                    if (StringUtils.isNotBlank(f.getValue())) {
                        try {
                            Metadata md = new Metadata(prefs.getMetadataTypeByName(sp.getOne()));
                            md.setValue(f.getValue());
                            item.addMetadata(md);
                        } catch (Exception e) {
                            log.error(e);
                        }
                    }

                    break;
                }

            }

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

    private VocabularyConfig getConfig(String templateName) {

        XMLConfiguration xmlConfig = ConfigPlugins.getPluginConfig(title);
        xmlConfig.setExpressionEngine(new XPathExpressionEngine());
        SubnodeConfiguration myconfig = null;
        if (StringUtils.isNotBlank(workflowTitle)) {
            try {
                myconfig = xmlConfig.configurationAt("//config[./workflow='" + workflowTitle + "'][./template='" + templateName + "']");
            } catch (IllegalArgumentException e) {
            }
            if (myconfig == null) {
                try {
                    myconfig = xmlConfig.configurationAt("//config[./workflow='" + workflowTitle + "']");
                } catch (IllegalArgumentException e) {
                }
            }
        }
        if (myconfig == null) {
            try {
                myconfig = xmlConfig.configurationAt("//config[./template='" + templateName + "']");
            } catch (IllegalArgumentException e) {
                myconfig = xmlConfig.configurationAt("//config[./template='*']");
            }
        }
        VocabularyConfig config = new VocabularyConfig(myconfig);
        return config;
    }

    @Override
    public List<ConfigOpacCatalogue> getOpacConfiguration(String workflowName, String title) {
        List<ConfigOpacCatalogue> answer = new ArrayList<>();
        this.workflowTitle = workflowName;
        ConfigOpacCatalogue coc = ConfigOpac.getInstance().getCatalogueByName(title);
        coc.setOpacPlugin(this);
        answer.add(coc);
        return answer;
    }

}