CREATE TABLE IF NOT EXISTS "android_metadata" ("locale" TEXT DEFAULT 'en_US');
INSERT OR REPLACE INTO "android_metadata" (ROWID, "locale") VALUES ((SELECT ROWID FROM "android_metadata" WHERE "locale" = 'en_US'), 'en_US');

CREATE TABLE IF NOT EXISTS "wigle_mcc_mnc" (
	"mcc" VARCHAR(3) NOT NULL,
	"mnc" VARCHAR(3) NOT NULL,
	"type" TEXT,
	"countryName" TEXT,
	"countryCode" CHAR(2),
	"brand" TEXT,
	"operator" TEXT,
	"status" TEXT,
	"bands" TEXT,
	"notes" TEXT,
	PRIMARY KEY (mcc, mnc)
) WITHOUT ROWID;