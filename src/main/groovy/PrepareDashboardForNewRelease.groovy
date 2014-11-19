import com.atlassian.jira.bc.JiraServiceContextImpl
import com.atlassian.jira.bc.issue.search.SearchService
import com.atlassian.jira.bc.portal.PortalPageService
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.search.SearchRequest
import com.atlassian.jira.issue.search.SearchRequestManager
import com.atlassian.jira.portal.PortalPage
import com.atlassian.jira.portal.PortletConfiguration
import com.atlassian.jira.portal.PortletConfigurationManager
import com.atlassian.jira.user.ApplicationUser

finalMessage = ""

def mainMethod() {
    // This script must be run from Jira -> Administration -> Add-ons -> Script Console

    // Here are some explanations so all this makes sense:
    // - the dashboards in Jira were previously called "portals" - even though they
    // were renamed in the web interface, they still have the same name in code.
    // A dashboard in Jira is represented by a "PortalPage" class and can be
    // managed by using the "PortalPageService".
    // - the gadgets are represented by a "PortletConfiguration" class
    // - these gadgets have a "userPrefs" map that can be changed based on need

    def portalPageService = ComponentAccessor.getComponent(PortalPageService.class)
    def userName = "admin"
    def user = ComponentAccessor.userManager.getUserByKey(userName)

    def idOfPortalPageToClone = 10100L
    def portalPage = portalPageService.getPortalPage(createServiceContext(user), idOfPortalPageToClone)
    def clonedPortalPage = clonePortalPage(user, portalPage)

    def gadgets = portalPageService
            .getPortletConfigurations(createServiceContext(user), clonedPortalPage.id)
            .flatten()
    def gadgetsWithFilters = findAllGadgetsWithFilters(gadgets)
    gadgets.each { gadget ->
        gadget.userPrefs.each { preference ->
            if(hasFilter(preference)) {
                def filterId = extractFilterIdFrom(preference)
                logMessage "The filterId is: $filterId"

                def searchRequestManager = ComponentAccessor.getComponent(SearchRequestManager.class)
                def filter = searchRequestManager.getSearchRequestById(filterId)
                def oldQuery = filter.query.queryString
                def newQuery = "key=DEMO-1"
                logMessage "The old query is: $oldQuery"
                logMessage "The new query is: $newQuery"

                def query = createQueryFromJqlQuery(newQuery)
                def newFilter = new SearchRequest(query)
                newFilter.name = "$filter.name NEW ${new Date()}"
                newFilter.owner = user
                newFilter.permissions = filter.permissions

                newFilter = searchRequestManager.create(newFilter)
                def newUserPrefs = new HashMap<>(gadget.userPrefs)
                newUserPrefs[preference.key] = "filter-$newFilter.id".toString()
                gadget.userPrefs = newUserPrefs
                def gadgetManager = ComponentAccessor.getComponent(PortletConfigurationManager.class)
                gadgetManager.store(gadget)
            }
        }
    }

    logMessage "I have cloned the specified dashboard!"
    logMessage "Here is the id: " + clonedPortalPage.id

    logImportantMessage "------------------"

    return finalMessage // the returned value is shown after the execution in Jira's Web Script Console
}

def hasFilter(Map.Entry<String, String> preference) {
    preference.value.toLowerCase().contains('filter')
}

def clonePortalPage(ApplicationUser user, PortalPage portalPage) {
    def favourite = true
    def portalPageService = ComponentAccessor.getComponent(PortalPageService.class)
    portalPageService.createPortalPageByClone(
            createServiceContext(user),
            createNewPortalPage(portalPage),
            portalPage.id,
            favourite)
}

def logMessage(Object message) {
    finalMessage += "${message}<br/>"
}

def logImportantMessage(Object message) {
    logMessage "<strong>${message}</strong>"
}

def createServiceContext(ApplicationUser user) {
    new JiraServiceContextImpl(user)
}

def createNewPortalPage(PortalPage portalPage) {
    PortalPage
            .portalPage(portalPage)
            .name("${portalPage.name} CLONED ON ${new Date()}")
            .build();
}

mainMethod()

def extractFilterIdFrom(Map.Entry<String, String> preference) {
    // filter-12345
    preference.value.replace("filter-", "").toLong()
}

def createQueryFromJqlQuery(String jqlQuery) {
    def user = ComponentAccessor.jiraAuthenticationContext.user.directoryUser
    def searchService = ComponentAccessor.getComponent(SearchService.class)
    return searchService.parseQuery(user, jqlQuery).getQuery()
}

def findAllGadgetsWithFilters(ArrayList<PortletConfiguration> gadgets) {
    def gadgetsWithFilters = []
    gadgets.each { gadget ->
        def withFilter = gadget.userPrefs.any { preference -> hasFilter(preference) }
        if (withFilter) gadgetsWithFilters << gadget
    }
    return gadgetsWithFilters
}
