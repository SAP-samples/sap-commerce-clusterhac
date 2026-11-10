import de.hybris.platform.core.Registry
import de.hybris.platform.hac.facade.HacCacheFacade
import de.hybris.platform.regioncache.region.CacheRegion
import de.hybris.platform.regioncache.region.CacheRegionProvider
import org.apache.commons.lang3.StringUtils

CacheRegionProvider cacheRegionProvider = (CacheRegionProvider)Registry.getApplicationContext().getBean("cacheRegionProvider")
HacCacheFacade hacCacheFacade =  (HacCacheFacade)Registry.getApplicationContext().getBean("hacCacheFacade")
String REGION_NAME = "???REGION_NAME???"
if(StringUtils.equalsIgnoreCase(REGION_NAME,"all")){
    hacCacheFacade.clearCache()
    out.println("Clearing cache for all region")
}
else{
    CacheRegion region = cacheRegionProvider.getRegionByName(REGION_NAME).get(0)
    out.println(region.getName())
    region.clearCache()
}
