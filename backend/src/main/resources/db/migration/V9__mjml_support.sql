-- Add MJML support columns
ALTER TABLE email_templates ADD (mjml_source CLOB, last_rendered_html CLOB);
