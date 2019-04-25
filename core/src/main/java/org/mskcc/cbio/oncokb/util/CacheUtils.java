package org.mskcc.cbio.oncokb.util;

import com.mysql.jdbc.StringUtils;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.lang3.math.NumberUtils;
import org.mskcc.cbio.oncokb.model.*;
import org.mskcc.cbio.oncokb.model.oncotree.TumorType;

import java.io.IOException;
import java.util.*;


/**
 * Created by Hongxin on 4/1/16.
 * <p/>
 * CacheUtils is used to manage cached variant summaries, relevant alterations, alterations which all gene based.
 * It also includes mapped tumor types which is based on query tumor type name + source.
 * <p/>
 * The GeneObservable manages all gene based caches. Any updates happen on gene will automatically trigger
 * GeneObservable to notify all observers to update relative cache.
 * <p/>
 * TODO:
 * Ideally, we should place cache functions in the cache BAO with a factory which controls the source of data.
 * In this way, user can easily to choose to get data from cache or database directly.
 */


public class CacheUtils {
    private static Map<Integer, Gene> genesByEntrezId = new HashMap<>();
    private static Map<String, Integer> hugoSymbolToEntrez = new HashMap<>();

    private static final String CANCER_GENE_FILE_PATH = "/data/cancer-gene.txt";
    private static List<CancerGene> cancerGeneList = null;

    private static Map<String, List<TumorType>> mappedTumorTypes = new HashMap<>();
    private static Map<String, List<TumorType>> allOncoTreeTypes = new HashMap<>(); //Tag by different categories. main or subtype
    private static Map<String, Object> numbers = new HashMap<>();

    private static String status = "enabled"; //Current cacheUtils status. Applicable value: disabled enabled

    // Cache data from database
    private static Set<Gene> genes = new HashSet<>();
    private static Set<Drug> drugs = new HashSet<>();
    private static Map<Integer, Set<Evidence>> evidences = new HashMap<>(); //Gene based evidences
    private static Map<Integer, Set<Alteration>> alterations = new HashMap<>(); //Gene based alterations
    private static Map<Integer, Set<Alteration>> VUS = new HashMap<>(); //Gene based VUSs

    // Other services which will be defined in the property cache.update separated by comma
    // Every time the observer is triggered, all other services will be triggered as well
    private static List<String> otherServices = new ArrayList<>();

    private static Map<String, Long> recordTime = new HashedMap();

    private static Observer numbersObserver = new Observer() {
        @Override
        public void update(Observable o, Object arg) {
            numbers.clear();
        }
    };

    private static Observer VUSObserver = new Observer() {
        @Override
        public void update(Observable o, Object arg) {
            Map<String, String> operation = (Map<String, String>) arg;
            if (operation.get("cmd") == "update") {
                Integer entrezGeneId = Integer.parseInt(operation.get("val"));
                VUS.remove(entrezGeneId);
            } else if (operation.get("cmd") == "reset") {
                VUS.clear();
            }
        }
    };

    private static Observer alterationsObserver = new Observer() {
        @Override
        public void update(Observable o, Object arg) {
            Map<String, String> operation = (Map<String, String>) arg;
            if (operation.get("cmd") == "update") {
                Integer entrezGeneId = Integer.parseInt(operation.get("val"));
                alterations.remove(entrezGeneId);
            } else if (operation.get("cmd") == "reset") {
                alterations.clear();
            }
        }
    };

    // Always update genes since everything is relying on this.
    private static Observer genesObserver = new Observer() {
        @Override
        public void update(Observable o, Object arg) {
            cacheAllGenes();
        }
    };

    private static Observer drugsObserver = new Observer() {
        @Override
        public void update(Observable o, Object arg) {
            drugs = new HashSet<>(ApplicationContextSingleton.getDrugBo().findAll());
        }
    };

    private static Observer evidencesObserver = new Observer() {
        @Override
        public void update(Observable o, Object arg) {
            Map<String, String> operation = (Map<String, String>) arg;
            if (operation.get("cmd") == "update") {
                Integer entrezGeneId = Integer.parseInt(operation.get("val"));
                evidences.remove(entrezGeneId);
            } else if (operation.get("cmd") == "reset") {
                evidences.clear();
                cacheAllEvidencesByGenes();
            }
        }
    };

    private static Observer allCancerTypesObserver = new Observer() {
        @Override
        public void update(Observable o, Object arg) {
            allOncoTreeTypes.put("main", TumorTypeUtils.getOncoTreeCancerTypes(ApplicationContextSingleton.getEvidenceBo().findAllCancerTypes()));
            allOncoTreeTypes.put("subtype", TumorTypeUtils.getOncoTreeSubtypesByCode(ApplicationContextSingleton.getEvidenceBo().findAllSubtypes()));
        }
    };

    private static void notifyOtherServices(String cmd, Set<Integer> entrezGeneIds) {
        if (cmd == null) {
            cmd = "";
        }
        System.out.println("Notify other services..." + " at " + MainUtils.getCurrentTime());
        if (cmd == "update" && entrezGeneIds != null) {
            for (String service : otherServices) {
                if (!StringUtils.isNullOrEmpty(service)) {
                    try {
                        HttpUtils.postRequest(service + "?cmd=updateGene&entrezGeneIds=" +
                            org.apache.commons.lang3.StringUtils.join(entrezGeneIds, ","), "");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else if (cmd == "reset") {
            for (String service : otherServices) {
                if (!StringUtils.isNullOrEmpty(service)) {
                    try {
                        HttpUtils.postRequest(service + "?cmd=reset", "");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    static {
        try {
            Long current = MainUtils.getCurrentTimestamp();
            GeneObservable.getInstance().addObserver(alterationsObserver);
            GeneObservable.getInstance().addObserver(allCancerTypesObserver);
            GeneObservable.getInstance().addObserver(genesObserver);
            GeneObservable.getInstance().addObserver(evidencesObserver);
            GeneObservable.getInstance().addObserver(VUSObserver);
            GeneObservable.getInstance().addObserver(numbersObserver);
            GeneObservable.getInstance().addObserver(drugsObserver);

            if (status.equals("enabled")) {
                System.out.println("Observer: " + MainUtils.getTimestampDiff(current) + " at " + MainUtils.getCurrentTime());

                cacheAllGenes();

                setAllAlterations();

                current = MainUtils.getCurrentTimestamp();
                drugs = new HashSet<>(ApplicationContextSingleton.getDrugBo().findAll());
                System.out.println("Cache all drugs: " + MainUtils.getTimestampDiff(current) + " at " + MainUtils.getCurrentTime());
                current = MainUtils.getCurrentTimestamp();

                Set<Evidence> geneEvidences = new HashSet<>(ApplicationContextSingleton.getEvidenceBo().findAll());
                System.out.println("Get all evidences: " + MainUtils.getTimestampDiff(current) + " at " + MainUtils.getCurrentTime());
                current = MainUtils.getCurrentTimestamp();

                Map<Gene, Set<Evidence>> mappedEvidence = EvidenceUtils.separateEvidencesByGene(genes, geneEvidences);

                System.out.println("Separate all evidences: " + MainUtils.getTimestampDiff(current) + " at " + MainUtils.getCurrentTime());
                current = MainUtils.getCurrentTimestamp();

                Iterator it = mappedEvidence.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Gene, Set<Evidence>> pair = (Map.Entry) it.next();
                    evidences.put(pair.getKey().getEntrezGeneId(), pair.getValue());
                }
                System.out.println("Cache all evidences: " + MainUtils.getTimestampDiff(current) + " at " + MainUtils.getCurrentTime());
                current = MainUtils.getCurrentTimestamp();

                for (Map.Entry<Integer, Set<Evidence>> entry : evidences.entrySet()) {
                    setVUS(entry.getKey(), entry.getValue());
                }
                System.out.println("Cache all VUSs: " + MainUtils.getTimestampDiff(current) + " at " + MainUtils.getCurrentTime());
            } else {
                System.out.println("CacheUtil is disabled at " + MainUtils.getCurrentTime());
            }
            current = MainUtils.getCurrentTimestamp();

            allOncoTreeTypes.put("main", TumorTypeUtils.getOncoTreeCancerTypes(ApplicationContextSingleton.getEvidenceBo().findAllCancerTypes()));
            allOncoTreeTypes.put("subtype", TumorTypeUtils.getOncoTreeSubtypesByCode(ApplicationContextSingleton.getEvidenceBo().findAllSubtypes()));

            System.out.println("Cache all tumor types: " + MainUtils.getTimestampDiff(current) + " at " + MainUtils.getCurrentTime());
            current = MainUtils.getCurrentTimestamp();

            NamingUtils.cacheAllAbbreviations();
            System.out.println("Cache abbreviation ontology: " + MainUtils.getTimestampDiff(current) + " at " + MainUtils.getCurrentTime());
            current = MainUtils.getCurrentTimestamp();

            registerOtherServices();
            System.out.println("Register other services: " + MainUtils.getTimestampDiff(current) + " at " + MainUtils.getCurrentTime());
            current = MainUtils.getCurrentTimestamp();

        } catch (Exception e) {
            System.out.println(e + " at " + MainUtils.getCurrentTime());
        }
    }

    private static void registerOtherServices() throws IOException {
        String services = PropertiesUtils.getProperties("cache.update");
        if (services != null) {
            otherServices = Arrays.asList(services.split(","));
        }
    }

    public static Gene getGeneByEntrezId(Integer entrezId) {
        if (genesByEntrezId.containsKey(entrezId)) {
            return genesByEntrezId.get(entrezId);
        } else {
            return null;
        }
    }

    public static Boolean containGeneByEntrezId(Integer entrezId) {
        return genesByEntrezId.containsKey(entrezId) ? true : false;
    }

    public static void setGeneByEntrezId(Gene gene) {
        if (gene != null) {
            genesByEntrezId.put(gene.getEntrezGeneId(), gene);
            hugoSymbolToEntrez.put(gene.getHugoSymbol(), gene.getEntrezGeneId());
        }
    }

    private static void cacheAllGenes() {
        Long current = MainUtils.getCurrentTimestamp();

        genes = new HashSet<>(ApplicationContextSingleton.getGeneBo().findAll());
        genesByEntrezId = new HashedMap();
        hugoSymbolToEntrez = new HashedMap();
        for (Gene gene : genes) {
            genesByEntrezId.put(gene.getEntrezGeneId(), gene);
            hugoSymbolToEntrez.put(gene.getHugoSymbol(), gene.getEntrezGeneId());
        }
        cancerGeneList = null;
        System.out.println("Cache all genes: " + MainUtils.getTimestampDiff(current) + " at " + MainUtils.getCurrentTime());
    }

    public static List<CancerGene> getCancerGeneList() {
        if(cancerGeneList == null) {
            updateCancerGeneList();
        }
        return cancerGeneList;
    }

    private static void updateCancerGeneList() {
        cancerGeneList = new ArrayList<>();
        try {
            List<String> lines = FileUtils.readTrimedLinesStream(
                CancerGeneUtils.class.getResourceAsStream(CANCER_GENE_FILE_PATH));
            Iterator itr = lines.iterator();
            Set<String> allHugoSymbolsFromFile = new HashSet<>();

            while (itr.hasNext()) {
                String line = itr.next().toString().trim();
                // skip comments
                if (line.startsWith("#")) {
                    continue;
                }

                String[] items = line.split("\t");
                if (items.length != 9) continue;

                String hugoSymbol = items[0];
                allHugoSymbolsFromFile.add(hugoSymbol);

                Gene gene = GeneUtils.getGeneByEntrezId(Integer.parseInt(items[1]));
                CancerGene cancerGene = new CancerGene();
                cancerGene.setEntrezGeneId(Integer.parseInt(items[1]));

                if (gene == null) {
                    cancerGene.setHugoSymbol(hugoSymbol);
                } else {
                    if (!gene.getHugoSymbol().equals(hugoSymbol)) {
                        System.out.println("The gene hugo does not match, expect " + gene.getHugoSymbol() + ", but got: " + hugoSymbol);
                    }
                    cancerGene.setHugoSymbol(gene.getHugoSymbol());
                    cancerGene.setOncokbAnnotated(true);
                    cancerGene.setOncogene(gene.getOncogene());
                    cancerGene.setTSG(gene.getTSG());
                }
                int occurence = NumberUtils.isNumber(items[2].trim()) ? Integer.parseInt(items[2].trim()) : 0;
                if (cancerGene.getOncokbAnnotated()) {
                    occurence++;
                }
                cancerGene.setOccurrenceCount(occurence);
                cancerGene.setmSKImpact(items[3].trim().equals("1"));
                cancerGene.setmSKHeme(items[4].trim().equals("1"));
                cancerGene.setFoundation(items[5].trim().equals("1"));
                cancerGene.setFoundationHeme(items[6].trim().equals("1"));
                cancerGene.setVogelstein(items[7].trim().equals("1"));
                cancerGene.setSangerCGC(items[8].trim().equals("1"));
                cancerGeneList.add(cancerGene);
            }

            // We also need to include genes that not in the initial list
            Set<Gene> allAnnotatedGenes = GeneUtils.getAllGenes();
            allAnnotatedGenes
                .stream()
                .filter(gene -> !allHugoSymbolsFromFile.contains(gene.getHugoSymbol()))
                .forEach(gene -> {
                    CancerGene cancerGene = new CancerGene();
                    cancerGene.setEntrezGeneId(gene.getEntrezGeneId());
                    cancerGene.setHugoSymbol(gene.getHugoSymbol());
                    cancerGene.setOncokbAnnotated(true);
                    cancerGene.setOccurrenceCount(1);
                    cancerGene.setOncogene(gene.getOncogene());
                    cancerGene.setTSG(gene.getTSG());
                    cancerGeneList.add(cancerGene);
                });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Gene getGeneByHugoSymbol(String hugoSymbol) {
        Integer entrezGeneId = hugoSymbolToEntrez.get(hugoSymbol);
        if (entrezGeneId == null)
            return null;

        return genesByEntrezId.get(entrezGeneId);
    }

    public static Boolean containGeneByHugoSymbol(String hugoSymbol) {
        Integer entrezGeneId = hugoSymbolToEntrez.get(hugoSymbol);
        if (entrezGeneId == null)
            return false;

        if (genesByEntrezId.get(entrezGeneId) == null) {
            return false;
        } else {
            return true;
        }
    }

    private static void setVUS(Integer entrezGeneId, Set<Evidence> evidences) {
        if (!VUS.containsKey(entrezGeneId)) {
            VUS.put(entrezGeneId, new HashSet<Alteration>());
        }
        VUS.put(entrezGeneId, AlterationUtils.findVUSFromEvidences(evidences));
    }

    public static Set<Alteration> getVUS(Integer entrezGeneId) {
        if (entrezGeneId == null) {
            return new HashSet<>();
        }
        if (VUS.containsKey(entrezGeneId)) {
            return VUS.get(entrezGeneId) == null ? new HashSet<Alteration>() : Collections.unmodifiableSet(VUS.get(entrezGeneId));
        } else {
            Gene gene = GeneUtils.getGeneByEntrezId(entrezGeneId);
            if (gene != null) {
                synEvidences();
            }
            return VUS.get(entrezGeneId) == null ? new HashSet<Alteration>() : Collections.unmodifiableSet(VUS.get(entrezGeneId));
        }
    }

    public static void setNumbers(String type, Object number) {
        numbers.put(type, number);
    }

    public static Object getNumbers(String type) {
        return numbers.get(type);
    }

    public static Set<Alteration> getAlterations(Integer entrezGeneId) {
        synAlterations();
        Set<Alteration> result = alterations.get(entrezGeneId);
        if (result == null) {
            return new HashSet<>();
        }else {
            return Collections.unmodifiableSet(result);
        }
    }

    public static Set<Alteration> findMutationsByConsequenceAndPosition(Gene gene, VariantConsequence consequence, int start, int end) {
        return AlterationUtils.findOverlapAlteration(getAlterations(gene.getEntrezGeneId()), gene, consequence, start, end);
    }

    public static Set<Alteration> findMutationsByConsequenceAndPositionOnSamePosition(Gene gene, VariantConsequence consequence, int start, int end) {
        Set<Alteration> alterations = new HashSet<>();
        for (Alteration alteration : getAlterations(gene.getEntrezGeneId())) {
            if (alteration.getConsequence().equals(consequence)
                && alteration.getProteinStart().equals(alteration.getProteinEnd())
                && alteration.getProteinStart() >= start
                && alteration.getProteinStart() <= end) {
                alterations.add(alteration);
            }
        }
        return alterations;
    }

    public static Boolean containAlterations(Integer entrezGeneId) {
        synAlterations();
        return alterations.containsKey(entrezGeneId) ? true : false;
    }

    public static void setAlterations(Gene gene) {
        if (gene != null && genes.contains(gene)) {
            alterations.put(gene.getEntrezGeneId(), new HashSet<>(ApplicationContextSingleton.getAlterationBo().findAlterationsByGene(Collections.singleton(gene))));
        }
    }

    public static List<TumorType> getMappedTumorTypes(String queryTumorType, String source) {
        return mappedTumorTypes.get(queryTumorType + "&" + source);
    }

    public static Boolean containMappedTumorTypes(String queryTumorType, String source) {
        return mappedTumorTypes.containsKey(queryTumorType + "&" + source) ? true : false;
    }

    public static void setMappedTumorTypes(String queryTumorType, String source, List<TumorType> tumorTypes) {
        mappedTumorTypes.put(queryTumorType + "&" + source, tumorTypes);
    }

    public static List<TumorType> getAllCancerTypes() {
        if (isEnabled()) {
            return allOncoTreeTypes.get("main");
        } else {
            return TumorTypeUtils.getOncoTreeCancerTypes(ApplicationContextSingleton.getEvidenceBo().findAllCancerTypes());
        }
    }

    public static List<TumorType> getAllSubtypes() {
        if (isEnabled()) {
            return allOncoTreeTypes.get("subtype");
        } else {
            return TumorTypeUtils.getOncoTreeSubtypesByCode(ApplicationContextSingleton.getEvidenceBo().findAllSubtypes());
        }
    }

    public static Set<Gene> getAllGenes() {
        if (genes.size() == 0) {
            genes = new HashSet<>(ApplicationContextSingleton.getGeneBo().findAll());
        }
        return genes;
    }

    private static void setAllAlterations() {
        Long current = MainUtils.getCurrentTimestamp();
        List<Alteration> allAlterations = ApplicationContextSingleton.getAlterationBo().findAll();

        for (Alteration alteration : allAlterations) {
            Gene gene = alteration.getGene();
            if (!alterations.containsKey(gene.getEntrezGeneId())) {
                alterations.put(gene.getEntrezGeneId(), new HashSet<Alteration>());
            }
            alterations.get(gene.getEntrezGeneId()).add(alteration);
        }
        System.out.println("Cache all alterations: " + MainUtils.getTimestampDiff(current) + " at " + MainUtils.getCurrentTime());
    }

    public static Set<Drug> getAllDrugs() {
        if (drugs.size() == 0) {
            drugs = new HashSet<>(ApplicationContextSingleton.getDrugBo().findAll());
        }
        return drugs;
    }

    public static Drug getPersistentDrug(Drug drug) {
        if (drug == null)
            return null;

        for (Drug persistent : drugs) {
            if (persistent.equals(drug))
                return persistent;
        }
        return null;
    }

    public static void addDrug(Drug drug) {
        if (drug != null) {
            drugs.add(drug);
        }
    }

    public static Set<Evidence> getAllEvidences() {
        Set<Evidence> evis = new HashSet<>();
        for (Map.Entry<Integer, Set<Evidence>> map : evidences.entrySet()) {
            evis.addAll(map.getValue());
        }
        return evis;
    }

    public static Set<Evidence> getEvidences(Gene gene) {
        if (gene == null) {
            return new HashSet<>();
        }

        synEvidences();

        if (evidences.containsKey(gene.getEntrezGeneId())) {
            return evidences.get(gene.getEntrezGeneId()) == null ? new HashSet<Evidence>() : Collections.unmodifiableSet(evidences.get(gene.getEntrezGeneId()));
        } else {
            return new HashSet<>();
        }
    }

    public static Set<Evidence> getEvidencesByIds(Set<Integer> ids) {
        synEvidences();

        Set<Evidence> mappedEvis = new HashSet<>();
        if (ids != null) {
            for (Map.Entry<Integer, Set<Evidence>> map : evidences.entrySet()) {
                for (Evidence evidence : map.getValue()) {
                    if (ids.contains(evidence.getId())) {
                        mappedEvis.add(evidence);
                    }
                }
            }
        }
        return mappedEvis;
    }

    public static Set<Evidence> getEvidencesByGenesAndIds(Set<Gene> genes, Set<Integer> ids) {
        synEvidences();

        Set<Evidence> mappedEvis = new HashSet<>();
        if (ids != null) {
            Set<Evidence> evidences = new HashSet<>();
            for (Gene gene : genes) {
                evidences.addAll(getEvidences(gene));
            }
            for (Evidence evidence : evidences) {
                if (ids.contains(evidence.getId())) {
                    mappedEvis.add(evidence);
                }
            }
        }
        return mappedEvis;
    }

    public static Set<Evidence> getEvidencesByUUID(String uuid) {
        synEvidences();

        Set<Evidence> mappedEvis = new HashSet<>();
        if (uuid != null) {
            for (Map.Entry<Integer, Set<Evidence>> map : evidences.entrySet()) {
                for (Evidence evidence : map.getValue()) {
                    if (uuid.equals(evidence.getUuid())) {
                        mappedEvis.add(evidence);
                    }
                }
            }
        }
        return mappedEvis;
    }

    public static Set<Evidence> getEvidencesByUUIDs(Set<String> uuids) {
        synEvidences();

        Set<Evidence> mappedEvis = new HashSet<>();
        if (uuids != null) {
            for (Map.Entry<Integer, Set<Evidence>> map : evidences.entrySet()) {
                for (Evidence evidence : map.getValue()) {
                    if (evidence != null && uuids.contains(evidence.getUuid())) {
                        mappedEvis.add(evidence);
                    }
                }
            }
        }
        return mappedEvis;
    }

    private static void setEvidences(Gene gene) {
        evidences.put(gene.getEntrezGeneId(), new HashSet<>(ApplicationContextSingleton.getEvidenceBo().findEvidencesByGeneFromDB(Collections.singleton(gene))));
    }

    private static void synEvidences() {
        Long current = MainUtils.getCurrentTimestamp();
        if (evidences == null || evidences.size() == 0) {
            cacheAllEvidencesByGenes();
        }

        if (evidences.keySet().size() != genes.size()) {
            for (Gene gene : genes) {
                if (!evidences.containsKey(gene.getEntrezGeneId())) {
                    setEvidences(gene);
                    setVUS(gene.getEntrezGeneId(), getEvidences(gene));
                }
            }
        }
    }

    private static void synAlterations() {
        Long current = MainUtils.getCurrentTimestamp();
        if (alterations == null || alterations.size() == 0) {
            setAllAlterations();
        }

        if (alterations.keySet().size() != genes.size()) {
            for (Gene gene : genes) {
                if (!alterations.containsKey(gene.getEntrezGeneId())) {
                    setAlterations(gene);
                }
            }
        }
    }

    public static void forceUpdateGeneAlterations(Integer entrezGeneId) {
        alterations.remove(entrezGeneId);
    }

    public static void updateGene(Set<Integer> entrezGeneIds, Boolean propagate) {
        System.out.println("Update gene on instance " + PropertiesUtils.getProperties("app.name") + " at " + MainUtils.getCurrentTime());
        if (propagate == null) {
            propagate = false;
        }
        if(entrezGeneIds == null){
            return;
        }
        entrezGeneIds.forEach(entrezGeneId -> GeneObservable.getInstance().update("update", entrezGeneId.toString()));
        if (propagate) {
            notifyOtherServices("update", entrezGeneIds);
        }
    }

    public static void resetAll() {
        System.out.println("Reset all genes cache on instance " + PropertiesUtils.getProperties("app.name") + " at " + MainUtils.getCurrentTime());
        GeneObservable.getInstance().update("reset", null);
        notifyOtherServices("reset", null);
    }

    public static void resetAll(Boolean propagate) {
        System.out.println("Reset all genes cache on instance " + PropertiesUtils.getProperties("app.name") + " at " + MainUtils.getCurrentTime());
        GeneObservable.getInstance().update("reset", null);
        if (propagate == null) {
            propagate = false;
        }
        if (propagate) {
            notifyOtherServices("reset", null);
        }
    }

    public static void enableCacheUtils() {
        status = "enabled";
    }

    public static void disableCacheUtils() {
        status = "disabled";
    }

    public static String getCacheUtilsStatus() {
        return status;
    }

    public static Boolean isEnabled() {
        if (status == "enabled") {
            return true;
        } else {
            return false;
        }
    }

    public static Boolean isDisabled() {
        if (status == "disabled") {
            return true;
        } else {
            return false;
        }
    }

    private static void cacheAllEvidencesByGenes() {
        Long current = MainUtils.getCurrentTimestamp();

        Map<Gene, Set<Evidence>> mappedEvidence =
            EvidenceUtils.separateEvidencesByGene(genes, new HashSet<>(
                ApplicationContextSingleton.getEvidenceBo().findAll()));
        Iterator it = mappedEvidence.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Gene, Set<Evidence>> pair = (Map.Entry) it.next();
            evidences.put(pair.getKey().getEntrezGeneId(), pair.getValue());
        }
        System.out.println("Cache all evidences by gene: " + MainUtils.getTimestampDiff(current) + " at " + MainUtils.getCurrentTime());
    }

    public static Map<String, Long> getRecordTime() {
        return recordTime;
    }

    public static void emptyRecordTime() {
        recordTime = new HashedMap();
    }

    public static void addRecordTime(String key, Long time) {
        if (!recordTime.containsKey(key))
            recordTime.put(key, (long) 0);
        recordTime.put(key, recordTime.get(key) + time);
    }
}
