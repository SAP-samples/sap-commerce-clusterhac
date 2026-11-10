configurationService = spring.getBean "configurationService"
Map<String, String> response = new HashMap<String, String>()
String CONFIG_KEY = "???CONFIG_KEY???"
String property = configurationService.getConfiguration().getString(CONFIG_KEY)
response.put("key", CONFIG_KEY)
response.put("current-value", property)
print org.apache.commons.lang3.StringUtils.join(response)