-- Add country code column to eg_user table
ALTER TABLE eg_user ADD COLUMN IF NOT EXISTS countrycode character varying(10);

-- Set default country code for existing users (optional - can be updated based on configuration)
UPDATE eg_user SET countrycode = '+91' WHERE countrycode IS NULL;
