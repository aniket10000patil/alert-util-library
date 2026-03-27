SELECT
    a.alert_id,
    a.alert_internal_id,
    JSON_OBJECT(
        'alertId' VALUE a.alert_id,
        'alertHighlights' VALUE JSON_OBJECT(
            'alert_id'             VALUE ah.alert_id,
            'business_date'        VALUE ah.alert_date,
            'alert_score'          VALUE ah.alert_score,
            'customer_name'        VALUE ah.entity_name,
            'customer_id'          VALUE ah.entity_id,
            'entity_currency'      VALUE ah.entity_currency,
            'party_risk_level'     VALUE ah.party_risk_level,
            'party_business_unit'  VALUE ah.business_unit,
            'narrative'            VALUE null,
            'threat_priority'      VALUE NULL,
            'guardrail'            VALUE NULL,
            'atl'                  VALUE NULL,
            'alert_deleted_rerun'  VALUE NULL,
            'is_enhanced_monitored' VALUE NULL,
            'trace_query_url'      VALUE NULL,
            'threats'              VALUE NULL
        RETURNING CLOB),

        'alertDetails' VALUE (
            SELECT JSON_ARRAYAGG(
                JSON_OBJECT(
                    'model'          VALUE dg.model,
                    'ruleset_id'     VALUE dg.rule_id,
                    'ruleset_name'   VALUE dg.rule_desc,
                    'source'         VALUE 'SAM8',
                    'guardrail'      VALUE null,
                    -- ↓ these now come from `d` instead of `dg`
                    'entity_type'    VALUE d.entity_type,
                    'entity_level'   VALUE d.entity_level,
                    'entity_id'      VALUE d.entity_key,
                    'detection_date' VALUE d.data_date,
                    'rule_logic'     VALUE d.threshold_detection,
                    'rule_recurrence' VALUE d.threshold_recurrence,
                    'excess_value'   VALUE d.excess_value,
                    'score'          VALUE dg.score,

                    'score_details' VALUE (
                        SELECT JSON_ARRAYAGG(
                            JSON_OBJECT(
                                'factor'       VALUE sd.factor,
                                'factor_value' VALUE sd.factor_value,
                                'factor_score' VALUE sd.factor_score
                            RETURNING CLOB)
                        RETURNING CLOB)
                        FROM XMLTABLE(
                            'score_details/score_detail'
                            PASSING d.event_score_xml   -- ← now from `d`
                            COLUMNS
                                factor       VARCHAR2(200) PATH 'factor',
                                factor_value VARCHAR2(200) PATH 'factor_value',
                                factor_score NUMBER        PATH 'factor_score'
                        ) sd
                    ),

                    'recurrences' VALUE (
                        SELECT JSON_ARRAYAGG(
                            JSON_OBJECT(
                                'event_id'          VALUE 'event_' || sam8_get_event_id(),
                                'occurence_id'      VALUE null,
                                'account_number'    VALUE rt.entity_number,
                                'business_date'     VALUE rt.date_value,
                                'detection_period'  VALUE rt.detection_period,
                                'feature_value'     VALUE rt.detection_value,
                                'profile_period'    VALUE rt.profile_period,
                                'profile_value'     VALUE rt.profile_value,
                                'excess_value'      VALUE rt.excess_value,
                                'feature_name'      VALUE NULL,
                                'feature_description' VALUE NULL,
                                'feature_alias'     VALUE NULL,
                                'associated_threats' VALUE NULL,
                                'associated_threat_priorities' VALUE NULL,

                                'score_details' VALUE (
                                    SELECT JSON_ARRAYAGG(
                                        JSON_OBJECT(
                                            'factor'       VALUE rsd.factor,
                                            'factor_value' VALUE rsd.factor_value,
                                            'factor_score' VALUE rsd.factor_score
                                        RETURNING CLOB)
                                    RETURNING CLOB)
                                    FROM XMLTABLE(
                                        'score_detail'
                                        PASSING rt.recur_score_details_xml
                                        COLUMNS
                                            factor       VARCHAR2(200) PATH 'factor',
                                            factor_value VARCHAR2(200) PATH 'factor_value',
                                            factor_score NUMBER        PATH 'factor_score'
                                    ) rsd
                                )
                            RETURNING CLOB)
                        RETURNING CLOB)
                        FROM XMLTABLE(
                            'data/recurrence_trans/data'
                            PASSING d.data_xml          -- ← now from `d`
                            COLUMNS
                                entity_number       VARCHAR2(50)  PATH 'entity_number',
                                date_value          VARCHAR2(20)  PATH 'date',
                                detection_period    VARCHAR2(200) PATH 'detection_period',
                                detection_value     VARCHAR2(200) PATH 'detection_value',
                                profile_period      VARCHAR2(200) PATH 'profile_period',
                                profile_value       VARCHAR2(200) PATH 'profile_value',
                                excess_value        VARCHAR2(200) PATH 'excess_value',
                                occurance_score     NUMBER        PATH 'occurance_score',
                                recur_score_details_xml XMLTYPE   PATH 'score_details/score_detail'
                        ) rt
                    )
                RETURNING CLOB)
            RETURNING CLOB)
            -- ↓ outer dg: rule-level fields only, extract data_xml as XMLTYPE
            FROM XMLTABLE(
                '/aml_alert/alert_details/data_group'
                PASSING XMLTYPE(a.html_file_key)
                COLUMNS
                    rule_id   VARCHAR2(400) PATH 'rule_id',
                    model     VARCHAR2(50)  PATH 'model',
                    rule_desc VARCHAR2(200) PATH 'rule_desc',
                    score     VARCHAR2(20)  PATH 'score',
                    -- collect all <data> nodes as XMLTYPE for cross join below
                    data_group_xml XMLTYPE  PATH '.'
            ) dg
            -- ↓ NEW: iterate over multiple <data> nodes inside each data_group
            CROSS JOIN XMLTABLE(
                'data_group/data'
                PASSING dg.data_group_xml
                COLUMNS
                    data_date            VARCHAR2(50)  PATH 'data_date',
                    entity_type          VARCHAR2(50)  PATH 'entity_type',
                    entity_key           VARCHAR2(50)  PATH 'entity_key',
                    threshold_detection  VARCHAR2(500) PATH 'threshold_detection',
                    threshold_recurrence VARCHAR2(500) PATH 'threshold_recurrence',
                    excess_value         VARCHAR2(500) PATH 'excess_value',
                    entity_level         VARCHAR2(50)  PATH 'is_le_or_pgr_entity',
                    data_xml             XMLTYPE       PATH '.',       -- for rt XMLTABLE
                    event_score_xml      XMLTYPE       PATH 'score_details'  -- for sd XMLTABLE
            ) d
        )

    RETURNING CLOB) AS alert_json
FROM sam_cm.alerts a,
XMLTABLE(
    '/aml_alert/alert_highlights'
    PASSING XMLTYPE(a.html_file_key)
    COLUMNS
        alert_id         VARCHAR2(500) PATH 'alert_id',
        alert_date       VARCHAR2(500) PATH 'alert_date',
        alert_score      VARCHAR2(500) PATH 'alert_score',
        entity_number    VARCHAR2(500) PATH 'entity_number',
        entity_name      VARCHAR2(500) PATH 'entity_name',
        entity_id        VARCHAR2(500) PATH 'entity_id',
        entity_currency  VARCHAR2(500) PATH 'entity_currency',
        region_currency  VARCHAR2(500) PATH 'region_currency',
        party_open_date  VARCHAR2(500) PATH 'custom_fields/custom_field[label="Party Open Date"]/value',
        party_risk_level VARCHAR2(500) PATH 'custom_fields/custom_field[label="Party Risk Level"]/value',
        business_unit    VARCHAR2(500) PATH 'custom_fields/custom_field[label="Business Unit"]/value',
        number_of_sars   NUMBER        PATH 'custom_fields/custom_field[label="Number Of SARs"]/value'
) ah
WHERE a.alert_internal_id = 83906;
