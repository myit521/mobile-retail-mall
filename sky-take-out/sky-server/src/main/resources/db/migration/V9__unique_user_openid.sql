-- Duplicate historical identities must be reviewed before this constraint is applied.
ALTER TABLE user ADD CONSTRAINT uk_user_openid UNIQUE (openid);
