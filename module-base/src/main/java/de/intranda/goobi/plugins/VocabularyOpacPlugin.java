package de.intranda.goobi.plugins;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import de.sub.goobi.helper.Helper;
import io.goobi.vocabulary.exchange.FieldInstance;
import io.goobi.vocabulary.exchange.TranslationInstance;
import io.goobi.vocabulary.exchange.Vocabulary;
import io.goobi.vocabulary.exchange.VocabularySchema;
import io.goobi.vocabulary.exchange.FieldDefinition;
import io.goobi.workflow.api.vocabulary.VocabularyAPIManager;
import io.goobi.workflow.api.vocabulary.jsfwrapper.JSFVocabularyRecord;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringUtils;
import org.goobi.production.cli.helper.StringPair;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IOpacPlugin;

import de.sub.goobi.config.ConfigPlugins;
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

    private VocabularyAPIManager vocabularyAPI = VocabularyAPIManager.getInstance();

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

        Vocabulary vocabulary = vocabularyAPI.vocabularies().findByName(database);

        VocabularySchema schema = vocabularyAPI.vocabularySchemas().get(vocabulary.getSchemaId());
        Optional<FieldDefinition> searchField = schema.getDefinitions().stream()
                .filter(d -> d.getName().equals(field))
                .findFirst();

        if (searchField.isEmpty()) {
            Helper.setFehlerMeldung("Field " + field + " not found in vocabulary " + vocabulary.getName());
            return null;
        }

        // Currently, no exact search is possible. Therefore, filter the result for exact results
        Optional<JSFVocabularyRecord> recordList = vocabularyAPI.vocabularyRecords()
                .search(vocabulary.getId(), searchField.get().getId() + ":" + term)
                .getContent()
                .stream()
                .filter(r -> r.getFields().stream()
                        .flatMap(f -> f.getValues().stream())
                        .flatMap(v -> v.getTranslations().stream())
                        .map(TranslationInstance::getValue)
                        .anyMatch(v -> v.equals(term)))
                .findFirst();

        if (recordList.isEmpty()) {
            // no records found
            hitcount = 0;
            return null;
        }

        hitcount = 1;
        JSFVocabularyRecord rec = recordList.get();

        Set<FieldInstance> fields = rec.getFields();
        String docStructType = config.getDefaultPublicationType();
        Optional<Long> publicationTypeFieldId = schema.getDefinitions().stream()
                .filter(d -> d.getName().equals(config.getPublicationTypeField()))
                .map(FieldDefinition::getId)
                .findFirst();
        if (publicationTypeFieldId.isPresent()) {
            docStructType = fields.stream()
                    .filter(f -> f.getDefinitionId().equals(publicationTypeFieldId.get()))
                    .flatMap(f -> f.getValues().stream())
                    .flatMap(v -> v.getTranslations().stream())
                    .map(TranslationInstance::getValue)
                    .collect(Collectors.joining(" "));
        }

        // create a FileFormat
        Fileformat mm = new MetsMods(prefs);
        DigitalDocument digitalDocument = new DigitalDocument();
        mm.setDigitalDocument(digitalDocument);
        DocStruct item = digitalDocument.createDocStruct(prefs.getDocStrctTypeByName(docStructType));
        digitalDocument.setLogicalDocStruct(item);
        // TODO: Check why item.getType() could be null
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
            Optional<Long> fieldId = schema.getDefinitions().stream()
                    .filter(d -> d.getName().equals(sp.getTwo()))
                    .map(FieldDefinition::getId)
                    .findFirst();
            if (fieldId.isPresent()) {
                String value = fields.stream()
                        .filter(f -> f.getDefinitionId().equals(fieldId.get()))
                        .flatMap(f -> f.getValues().stream())
                        .flatMap(v -> v.getTranslations().stream())
                        .map(TranslationInstance::getValue)
                        .collect(Collectors.joining(" "));
                if (value.isBlank()) {
                    continue;
                }
                try {
                    Metadata md = new Metadata(prefs.getMetadataTypeByName(sp.getOne()));
                    md.setValue(value);
                    item.addMetadata(md);
                } catch (Exception e) {
                    log.error(e);
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