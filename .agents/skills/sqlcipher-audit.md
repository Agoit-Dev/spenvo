# Skill: sqlcipher-audit

**Activate:** in M1 (first SQLCipher integration) and on any encrypted DB review.

## Steps
1. Verify `SpenvoDatabase.build` uses `SupportOpenHelperFactory` with a byte[] passphrase.
2. Verify the passphrase: generated on first use, stored in Android Keystore (AES),
   NEVER hardcoded or in cleartext prefs.
3. Verify `exportSchema=true` + versioned `room.schemaLocation` in `core/data/schemas/`
   (and that `doc/database/schema.mdd` reflects the current schema version).
4. Verify migrations: Room migration with SQLCipher requires a custom `SupportSQLiteOpenHelper`;
   test the vN→vN+1 upgrade.
5. Verify the passphrase does not stay in memory longer than necessary.
6. Run an instrumented test: create DB → insert → close → reopen → read (encryption OK).

## API reference
- `SupportOpenHelperFactory(byte[])` + `openHelperFactory(...)` in Room.
- Convert CharArray: `String(passphrase).toByteArray(Charsets.UTF_8)` (4.18.0:
  `SQLiteDatabase.getBytes` is private, do not use).