import de.hybris.platform.util.Config

String LOG_ENABLED = "???LOG_ENABLED???"
String TRACES_ENABLED = "???TRACES_ENABLED???"
String PARAMS_ENABLED = "???PARAMS_ENABLED???"

Config.setParameter("db.log.active", LOG_ENABLED)
Config.setParameter("db.log.sql.parameters", TRACES_ENABLED)
Config.setParameter("db.log.appendStackTrace", PARAMS_ENABLED)

return Config.getParameter("db.log.active")