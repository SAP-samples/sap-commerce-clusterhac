
configurationService = spring.getBean "configurationService"
Map<String, String> response = new HashMap<String, String>()
String CONFIG_KEY = "???CONFIG_KEY???"
String CONFIG_VALUE = "???CONFIG_VALUE???"
String property = configurationService.getConfiguration().getString(CONFIG_KEY)
response.put("key", CONFIG_KEY)
response.put("old-value", property)
de.hybris.platform.core.Registry.getCurrentTenant().getConfig().setParameter(CONFIG_KEY, CONFIG_VALUE)
property = configurationService.getConfiguration().getString(CONFIG_KEY)
response.put("new-value", property)
print org.apache.commons.lang3.StringUtils.join(response)