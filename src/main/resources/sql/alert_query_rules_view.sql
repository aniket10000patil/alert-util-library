-- Alert Query Rules View Query
-- Extracts query_rules JSON from the alert XML stored in sam_cm.alerts.html_file_key
--
-- Fix: rule_occurrences was returning NULL because the outer XMLTABLE was capturing
-- the entire <data> context node via PATH '.' (query_rule_data_xml) and passing it
-- to a nested XMLTABLE with XPath 'rule_occurrences/data'. Oracle does not reliably
-- propagate such PATH '.' fragments through nested correlated XMLTABLE references.
--
-- Fix applied: mirror the working score_detail_xml pattern — capture the specific
-- <rule_occurrences> node directly as rule_occurrences_xml XMLTYPE PATH 'rule_occurrences',
-- then iterate its <data> children with XPath 'data'.

SELECT
    a.alert_id,
    a.alert_internal_id,
    JSON_OBJECT(
        'alertId' VALUE a.alert_id,
        'queryRules' VALUE (
            SELECT JSON_ARRAYAGG(
                JSON_OBJECT(
                    'policy_type'      VALUE qr.policy_type,
                    'rule_name'        VALUE qr.rule_name,
                    'total_value'      VALUE qr.total_value,
                    'rule_score'       VALUE qr.rule_score,
                    'issue_timestamp'  VALUE qr.issue_timestamp,
                    'rule_logic'       VALUE qr.rule_logic,

                    'score_details' VALUE (
                        SELECT JSON_ARRAYAGG(
                            JSON_OBJECT(
                                'factor'       VALUE sd.factor,
                                'factor_value' VALUE sd.factor_value,
                                'factor_score' VALUE sd.factor_score
                            RETURNING CLOB)
                        RETURNING CLOB)
                        FROM XMLTABLE(
                            '.'
                            PASSING qr.score_detail_xml
                            COLUMNS
                                factor       VARCHAR2(200) PATH 'factor',
                                factor_value VARCHAR2(200) PATH 'factor_value',
                                factor_score NUMBER        PATH 'factor_score'
                        ) sd
                    ),

                    -- FIX: was 'rule_occurrences/data' PASSING qr.query_rule_data_xml
                    --      (query_rule_data_xml used PATH '.' which Oracle cannot reliably
                    --       propagate through nested correlated XMLTABLE references)
                    -- NOW: capture <rule_occurrences> node directly as rule_occurrences_xml,
                    --      then iterate its <data> children with XPath 'data'
                    'rule_occurrences' VALUE (
                        SELECT JSON_ARRAYAGG(
                            JSON_OBJECT(
                                'event_id'      VALUE 'event_' || 1,
                                'entity_number' VALUE ro.entity_number,
                                'business_date' VALUE ro.date_value,
                                'score'         VALUE ro.score,

                                'detection_variables' VALUE (
                                    SELECT JSON_ARRAYAGG(
                                        JSON_OBJECT(
                                            'variable_no'    VALUE dv.detection_variable_no,
                                            'variable_name'  VALUE dv.detection_variable,
                                            'detected_value' VALUE dv.detected_value
                                        RETURNING CLOB)
                                    RETURNING CLOB)
                                    FROM XMLTABLE(
                                        'data'
                                        PASSING ro.detection_value_xml
                                        COLUMNS
                                            detection_variable_no NUMBER        PATH 'detectionVariableNo',
                                            detection_variable    VARCHAR2(200) PATH 'detectionVariable',
                                            detected_value        VARCHAR2(200) PATH 'detectedValue'
                                    ) dv
                                )
                            RETURNING CLOB)
                        RETURNING CLOB)
                        FROM XMLTABLE(
                            'data'
                            PASSING qr.rule_occurrences_xml
                            COLUMNS
                                entity_number       VARCHAR2(50)  PATH 'entity_number',
                                date_value          VARCHAR2(20)  PATH 'date',
                                score               NUMBER        PATH 'score',
                                detection_value_xml XMLTYPE       PATH 'detection_value'
                        ) ro
                    )

                RETURNING CLOB)
            RETURNING CLOB)
            FROM XMLTABLE(
                '/aml_alert/alert_details/query_rules/data/query_rule/data'
                PASSING XMLTYPE(a.html_file_key)
                COLUMNS
                    policy_type          VARCHAR2(500) PATH 'policy_type',
                    rule_name            VARCHAR2(500) PATH 'rule_name',
                    total_value          VARCHAR2(500) PATH 'total_value',
                    rule_score           VARCHAR2(20)  PATH 'rule_score',
                    issue_timestamp      VARCHAR2(50)  PATH 'issue_timestamp',
                    rule_logic           CLOB          PATH 'rule_logic',
                    score_detail_xml     XMLTYPE       PATH 'score_detail',
                    rule_occurrences_xml XMLTYPE       PATH 'rule_occurrences'
            ) qr
        )
    RETURNING CLOB) AS query_rules_json
FROM sam_cm.alerts a
WHERE a.alert_id = 'SAM6-997005';
