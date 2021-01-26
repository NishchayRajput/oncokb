package org.mskcc.cbio.oncokb;

import org.mskcc.cbio.oncokb.model.ReferenceGenome;

/**
 * Application constants.
 */
public final class Constants {

    public static final String MISSENSE_VARIANT = "missense_variant";
    public static final String FIVE_UTR = "5_prime_UTR_variant";
    public static final String UPSTREAM_GENE = "upstream_gene_variant";

    public static final String PUBLIC_API_VERSION = "v1.2.1";
    public static final String PRIVATE_API_VERSION = "v1.3.0";

    // Defaults
    public static final String SWAGGER_DEFAULT_DESCRIPTION="OncoKB, a comprehensive and curated precision oncology knowledge base, offers oncologists detailed, evidence-based information about individual somatic mutations and structural alterations present in patient tumors with the goal of supporting optimal treatment decisions.";

    // Config property names
    public static final String IS_PUBLIC_INSTANCE = "is_public_instance";
    public static final String SWAGGER_DESCRIPTION = "swagger_description";

    public static final ReferenceGenome DEFAULT_REFERENCE_GENOME = ReferenceGenome.GRCh37;

    private Constants() {
    }
}
