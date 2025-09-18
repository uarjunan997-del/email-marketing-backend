-- V42: Seed default variable bindings for common variables
-- Map common variables to contact columns or system values for existing templates

DECLARE
  CURSOR c_templates IS SELECT id FROM email_templates;
  v_exists NUMBER;
BEGIN
  FOR t IN c_templates LOOP
    -- first_name -> contacts.first_name
    SELECT COUNT(*) INTO v_exists FROM template_variable_bindings WHERE template_id=t.id AND var_name='first_name';
    IF v_exists = 0 THEN
      INSERT INTO template_variable_bindings(template_id, var_name, source_type, source_key, default_value)
      VALUES (t.id, 'first_name', 'CONTACT_COLUMN', 'first_name', NULL);
    END IF;

    -- last_name -> contacts.last_name
    SELECT COUNT(*) INTO v_exists FROM template_variable_bindings WHERE template_id=t.id AND var_name='last_name';
    IF v_exists = 0 THEN
      INSERT INTO template_variable_bindings(template_id, var_name, source_type, source_key, default_value)
      VALUES (t.id, 'last_name', 'CONTACT_COLUMN', 'last_name', NULL);
    END IF;

    -- company_name -> custom field (company_name)
    SELECT COUNT(*) INTO v_exists FROM template_variable_bindings WHERE template_id=t.id AND var_name='company_name';
    IF v_exists = 0 THEN
      INSERT INTO template_variable_bindings(template_id, var_name, source_type, source_key, default_value)
      VALUES (t.id, 'company_name', 'CUSTOM_FIELD', 'company_name', NULL);
    END IF;

    -- unsubscribe_url -> system
    SELECT COUNT(*) INTO v_exists FROM template_variable_bindings WHERE template_id=t.id AND var_name='unsubscribe_url';
    IF v_exists = 0 THEN
      INSERT INTO template_variable_bindings(template_id, var_name, source_type, source_key, default_value)
      VALUES (t.id, 'unsubscribe_url', 'SYSTEM', 'unsubscribe_url', NULL);
    END IF;
  END LOOP;
END;
/
