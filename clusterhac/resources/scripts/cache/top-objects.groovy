import de.hybris.platform.core.Registry
import de.hybris.platform.regioncache.region.CacheRegion
import de.hybris.platform.regioncache.region.CacheRegionProvider
import de.hybris.platform.regioncache.key.CacheKey
import de.hybris.platform.regioncache.key.legacy.LegacyCacheKey

// Lists the top-N cache entries for a region, grouped by a MEANINGFUL signature, using only
// the public CacheRegion API (getAllKeys). No reflection — safe across platform versions.
//
// The useful differentiator depends on the region:
//   - Query caches: every entry shares the type-code __QUERY_CACHE__, so grouping by type-code
//     is useless. Instead we group by a normalised query signature derived from the legacy
//     key array (the query shape), so you see WHICH queries dominate the cache.
//   - Entity / other caches: the type-code (item type) is the useful grouping.
//
// Emits one line per bucket:  count \t signature \t typeCode \t valueType \t table \t sampleKey
// (table = the FROM-clause table for SQL query entries, else empty — used for the by-table
//  roll-up view; entity/other regions leave it empty and ignore it.)
// plus a trailing summary line: __TOTAL__ \t <totalEntries> \t <distinctBuckets>

String REGION_NAME = "???REGION_NAME???"
int LIMIT = Integer.parseInt("???LIMIT???")

CacheRegionProvider cacheRegionProvider =
        (CacheRegionProvider) Registry.getApplicationContext().getBean("cacheRegionProvider")

// Resolve numeric type-codes to item-type names (e.g. 30 -> Media). Cached per run.
def typeManager = Registry.getCurrentTenant().getJaloConnection().getTypeManager()
Map<String, String> typeNameCache = new HashMap<>()
def typeName = { Object typeCode ->
    String tc = String.valueOf(typeCode)
    if (typeNameCache.containsKey(tc)) { return typeNameCache.get(tc) }
    String name = tc
    if (tc ==~ /\d+/) {
        try {
            def ct = typeManager.getRootComposedType(Integer.parseInt(tc))
            if (ct != null && ct.getCode() != null) { name = ct.getCode() }
        } catch (Exception ignored) { /* keep numeric code */ }
    }
    typeNameCache.put(tc, name)
    return name
}

def regions = cacheRegionProvider.getRegionByName(REGION_NAME)
if (regions == null || regions.isEmpty()) {
    out.println("__ERROR__\tRegion not found: ${REGION_NAME}")
    return
}
CacheRegion region = regions.get(0)

Collection<CacheKey> keys
try {
    keys = region.getAllKeys()
} catch (Exception e) {
    out.println("__UNAVAILABLE__\tEntry-level introspection unavailable for this region type (${e.getClass().getSimpleName()}).")
    return
}
if (keys == null) {
    out.println("__UNAVAILABLE__\tEntry-level introspection returned no key set for this region type.")
    return
}

// Derive a stable, human-readable signature for a cache key.
//
// Query cache holds two key shapes (confirmed against a live 2211 instance):
//   FlexibleSearchCacheKey -> toString() = "FlexibleSearchCacheKey:sql:SELECT ... WHERE (.. = ?)"
//        The SQL (with ? placeholders) IS the signature; identical query shapes group naturally.
//   FinderResultCacheKey   -> legacy array [hjmp, find, <typecode>, <finderMethod>, [<target>]]
//        Signature = finderMethod + target (e.g. "ejbFindByCodeExact | media").
// Entity / other caches -> the type-code (item type) is the useful grouping.
def signatureFor = { CacheKey key ->
    String s = String.valueOf(key)

    // FlexibleSearch queries: use the SQL text, normalised (collapse whitespace).
    int sqlIdx = s.indexOf(":sql:")
    if (sqlIdx >= 0) {
        String sql = s.substring(sqlIdx + 5).trim().replaceAll(/\s+/, ' ')
        // Drop trailing key metadata if present (", valueType=..." etc.)
        int cut = sql.indexOf(", valueType=")
        if (cut > 0) { sql = sql.substring(0, cut) }
        return sql.length() > 160 ? sql.substring(0, 160) + '…' : sql
    }

    // Legacy finder results: pull finderMethod + target from the key array.
    if (key instanceof LegacyCacheKey) {
        Object[] parts = ((LegacyCacheKey) key).getLegacyKey()
        if (parts != null && parts.length > 0) {
            // Look for an ejbFindBy* token and the following target element.
            for (int i = 0; i < parts.length; i++) {
                String tok = String.valueOf(parts[i])
                if (tok != null && tok.startsWith("ejbFind")) {
                    String target = (i + 1 < parts.length) ? String.valueOf(parts[i + 1]) : ''
                    target = target.replaceAll(/[\[\]]/, '')
                    return target ? "${tok} | ${target}".toString() : tok
                }
            }
        }
    }

    // Fallback: the type-code resolved to an item-type name — the useful grouping for
    // entity/other caches (e.g. 30 -> Media).
    return typeName(key.getTypeCode())
}

// Extract the FROM-clause table from a SQL signature (first table if JOINs are present), so the
// client can roll entries up per table. Empty for non-SQL (legacy/entity) buckets.
def tableFor = { String signature ->
    if (signature == null) { return '' }
    def m = (signature =~ /(?i)\bFROM\s+([A-Za-z0-9_]+)/)
    return m ? m.group(1) : ''
}

// Aggregate by signature; keep type-code, value-type and a sample key per bucket.
Map<String, Integer> counts = new HashMap<>()
Map<String, String> meta = new HashMap<>()   // bucket -> "typeCode\tvalueType\ttable\tsample"
int total = 0
for (CacheKey key in keys) {
    total++
    String bucket = signatureFor(key)
    counts.put(bucket, (counts.getOrDefault(bucket, 0)) + 1)
    if (!meta.containsKey(bucket)) {
        String sample = String.valueOf(key)
        if (sample != null && sample.length() > 140) {
            sample = sample.substring(0, 140) + "…"
        }
        meta.put(bucket, "${typeName(key.getTypeCode())}\t${key.getCacheValueType()}\t${tableFor(bucket)}\t${sample}".toString())
    }
}

// Sort buckets by count desc, take top-N.
def sorted = counts.entrySet().sort { a, b -> b.getValue() <=> a.getValue() }
int emitted = 0
for (entry in sorted) {
    if (emitted >= LIMIT) {
        break
    }
    String bucket = entry.getKey()
    int count = entry.getValue()
    String m = meta.get(bucket) ?: "\t\t\t"
    out.println("${count}\t${bucket}\t${m}")
    emitted++
}
out.println("__TOTAL__\t${total}\t${counts.size()}")
