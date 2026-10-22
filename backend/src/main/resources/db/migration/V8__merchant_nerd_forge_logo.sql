UPDATE merchant
SET logo_url = '/branding/nerd-forge-logo.png'
WHERE slug = 'nerd-forge'
  AND (logo_url IS NULL OR logo_url = '');
