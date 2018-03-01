package org.georchestra.console.ws.backoffice.users;

import org.georchestra.console.dao.AdminLogDao;
import org.georchestra.console.ds.AccountDaoImpl;
import org.georchestra.console.ds.DataServiceException;
import org.georchestra.console.ds.DuplicatedEmailException;
import org.georchestra.console.ds.RoleDaoImpl;
import org.georchestra.console.ds.OrgsDao;
import org.georchestra.console.dto.Account;
import org.georchestra.console.dto.AccountFactory;
import org.georchestra.console.dto.UserSchema;
import org.georchestra.console.ws.backoffice.roles.RoleProtected;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.DistinguishedName;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.support.LdapNameBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.naming.Name;
import javax.naming.directory.SearchControls;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;

public class UsersControllerTest {
    private LdapTemplate ldapTemplate ;
    private LdapContextSource contextSource ;

    private UsersController usersCtrl ;
    private AccountDaoImpl dao ;
    private RoleDaoImpl roleDao ;
    private UserRule userRule ;
    private RoleProtected roles;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @Before
    public void setUp() throws Exception {
        userRule = new UserRule();
        userRule.setListOfprotectedUsers(new String[] { "geoserver_privileged_user" });

        ldapTemplate = Mockito.mock(LdapTemplate.class);
        contextSource = Mockito.mock(LdapContextSource.class);
        roles = Mockito.mock(RoleProtected.class);
        AdminLogDao logDao = Mockito.mock(AdminLogDao.class);


        Mockito.when(contextSource.getBaseLdapPath())
            .thenReturn(new DistinguishedName("dc=georchestra,dc=org"));
        Mockito.when(ldapTemplate.getContextSource()).thenReturn(contextSource);
        Mockito.when(roles.isProtected(Mockito.eq("USER"))).thenReturn(true);

        // Configures roleDao
        roleDao = new RoleDaoImpl();
        roleDao.setLdapTemplate(ldapTemplate);
        roleDao.setRoleSearchBaseDN("ou=roles");
        roleDao.setUniqueNumberField("ou");
        roleDao.setUserSearchBaseDN("ou=users");
        roleDao.setRoles(this.roles);
        roleDao.setLogDao(logDao);

        OrgsDao orgsDao = new OrgsDao();
        orgsDao.setLdapTemplate(ldapTemplate);
        orgsDao.setOrgsSearchBaseDN("ou=orgs");
        orgsDao.setUserSearchBaseDN("ou=users");


        // configures AccountDao
        dao = new AccountDaoImpl(ldapTemplate, roleDao, orgsDao);
        dao.setUniqueNumberField("employeeNumber");
        dao.setUserSearchBaseDN("ou=users");
        dao.setRoleDao(roleDao);
        dao.setLogDao(logDao);

        usersCtrl = new UsersController(dao, userRule);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        // Set user connected through http header
        request.addHeader("sec-username", "testadmin");
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testFindAllException() throws Exception {
        Mockito.doThrow(DataServiceException.class).when(ldapTemplate).search((Name) Mockito.any(),
                Mockito.anyString(), (ContextMapper) Mockito.any());
        try {
            usersCtrl.findAll(request, response);
        } catch (Throwable e) {
            assertTrue(e instanceof IOException);
            assertTrue(response.getStatus() == HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

	@Test
	public void testFindByUidEmpty() throws Exception {
		
		usersCtrl.findByUid("nonexistentuser", response);
		String a = response.getContentAsString();
		JSONObject ret = new JSONObject(response.getContentAsString());
		assertTrue(response.getStatus() == HttpServletResponse.SC_NOT_FOUND);
		assertFalse(ret.getBoolean("success"));
		assertTrue(ret.getString("error").equals("not_found"));

	}

    @Test
    public void testFindByUidProtected() throws Exception {
        usersCtrl.findByUid("geoserver_privileged_user", response);

        JSONObject ret = new JSONObject(response.getContentAsString());
        assertTrue(response.getStatus() == HttpServletResponse.SC_CONFLICT);
        assertFalse(ret.getBoolean("success"));
        assertTrue(ret.getString("error").equals("The user is protected: geoserver_privileged_user"));
    }

    @Test
    public void testFindByUidNotFound() throws Exception {
        Mockito.doThrow(NameNotFoundException.class).when(ldapTemplate).lookup((Name) Mockito.any(), (ContextMapper) Mockito.any());

        usersCtrl.findByUid("notfounduser", response);

        JSONObject ret = new JSONObject(response.getContentAsString());
        assertTrue(response.getStatus() == HttpServletResponse.SC_NOT_FOUND);
        assertFalse(ret.getBoolean("success"));
        assertTrue(ret.getString("error").equals("not_found"));
    }

    @Test
    public void testFindByUidDataServiceException() throws Exception {
        Mockito.doThrow(DataServiceException.class).when(ldapTemplate).lookup((Name) Mockito.any(), (ContextMapper) Mockito.any());

        try {
            usersCtrl.findByUid("failingUser", response);
        } catch (Throwable e) {
            assertTrue(e instanceof IOException);
            assertTrue(response.getStatus() == HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Test
    public void testFindByUid() throws Exception {
        Account pmauduit = AccountFactory.createBrief("pmauduit", "monkey123", "Pierre", "Mauduit",
                "pmauduit@localhost", "+33123456789", "developer", "");

        Mockito.when(ldapTemplate.lookup(Mockito.any(DistinguishedName.class), eq(UserSchema.ATTR_TO_RETRIEVE), (ContextMapper) Mockito.any())).thenReturn(pmauduit);
        usersCtrl.findByUid("pmauduit", response);

        JSONObject ret = new JSONObject(response.getContentAsString());
        assertTrue(response.getStatus() == HttpServletResponse.SC_OK);
        assertTrue(ret.getString("uid").equals("pmauduit"));
        assertTrue(ret.getString("mail").equals("pmauduit@localhost"));
        assertTrue(ret.getString("title").equals("developer"));
        assertTrue(ret.getString("sn").equals("Mauduit"));
        assertTrue(ret.getString("description").isEmpty());
        assertTrue(ret.getString("telephoneNumber").equals("+33123456789"));
        assertTrue(ret.getString("givenName").equals("Pierre"));
    }

    @Test
    public void testCreateProtectedUser() throws Exception {
        JSONObject reqUsr = new JSONObject().
                put("sn", "geoserver privileged user").
                put("givenName", "geoserver").
                put("mail", "geoserver@localhost").
                put("telephoneNumber", "+331234567890").
                put("facsimileTelephoneNumber", "+33123456788").
                put("street", "Avenue des Ducs de Savoie").
                put("postalCode", "73000").
                put("l", "Chambéry").
                put("postOfficeBox", "1234").
                put("o", "GeoServer");
        request.setRequestURI("/console/users/geoserver");
        // geoserver_privileged_user is not a valid username automatically generated
        userRule.setListOfprotectedUsers(new String[]{"geoserver_privileged_user", "ggeoserverprivilegeduser"});
        request.setContent(reqUsr.toString().getBytes());
        Mockito.doThrow(NameNotFoundException.class).when(ldapTemplate).lookup((Name) Mockito.any());

        usersCtrl.create(request, response);

        JSONObject ret = new JSONObject(response.getContentAsString());
        assertTrue(response.getStatus() == HttpServletResponse.SC_CONFLICT);
        assertFalse(ret.getBoolean("success"));
        assertTrue(ret.getString("error").equals("The user is protected: ggeoserverprivilegeduser"));
    }

    @Test
    public void testCreateIllegalArgumentException() throws Exception {
        JSONObject reqUsr = new JSONObject().
                put("sn", "geoserver privileged user").
                put("telephoneNumber", "+331234567890").
                put("facsimileTelephoneNumber", "+33123456788").
                put("street", "Avenue des Ducs de Savoie").
                put("postalCode", "73000").
                put("l", "Chambéry").
                put("postOfficeBox", "1234").
                put("o", "GeoServer");
        request.setRequestURI("/console/users/geoserver");
        request.setContent(reqUsr.toString().getBytes());
        Mockito.doThrow(NameNotFoundException.class).when(ldapTemplate).lookup((Name) Mockito.any());

        usersCtrl.create(request, response);

        JSONObject ret = new JSONObject(response.getContentAsString());
        assertTrue(response.getStatus() == HttpServletResponse.SC_CONFLICT);
        assertFalse(ret.getBoolean("success"));
        assertTrue(ret.getString("error").equals("First Name is required"));
    }

    @Test
    public void testCreateDuplicateEmailException() throws Exception {
        JSONObject reqUsr = new JSONObject().
                put("sn", "geoserver privileged user").
                put("mail","tomcat@localhost").
                put("givenName", "GS Priv User").
                put("telephoneNumber", "+331234567890").
                put("facsimileTelephoneNumber", "+33123456788").
                put("street", "Avenue des Ducs de Savoie").
                put("postalCode", "73000").
                put("l", "Chambéry").
                put("postOfficeBox", "1234").
                put("o", "GeoServer");
        request.setRequestURI("/console/users/geoserver");
        request.setContent(reqUsr.toString().getBytes());
        Mockito.doThrow(DuplicatedEmailException.class).when(ldapTemplate).lookup((Name) Mockito.any());

        usersCtrl.create(request, response);

        JSONObject ret = new JSONObject(response.getContentAsString());
        assertTrue(response.getStatus() == HttpServletResponse.SC_CONFLICT);
        assertFalse(ret.getBoolean("success"));
        assertTrue(ret.getString("error").equals("duplicated_email"));
    }

    @Test
    public void createUser() throws Exception {
        JSONObject reqUsr = new JSONObject().
                put("sn", "geoserver privileged user").
                put("mail","tomcat@localhost").
                put("givenName", "GS Priv User").
                put("telephoneNumber", "+331234567890").
                put("facsimileTelephoneNumber", "+33123456788").
                put("street", "Avenue des Ducs de Savoie").
                put("postalCode", "73000").
                put("l", "Chambéry").
                put("postOfficeBox", "1234");
        request.setRequestURI("/console/users/geoserver");
        request.setContent(reqUsr.toString().getBytes());
        Mockito.doThrow(NameNotFoundException.class).when(ldapTemplate).lookup((Name) Mockito.any());
        // TODO: Why 2 different codes checking that the user exists ?
        Mockito.doThrow(NameNotFoundException.class).when(ldapTemplate).lookup((Name) Mockito.any(), (ContextMapper) Mockito.any());


        Mockito.when(ldapTemplate.search((Name) Mockito.any(), Mockito.anyString(),(ContextMapper) Mockito.any()))
            .thenReturn(new ArrayList<Object>());

        Mockito.when(ldapTemplate.lookupContext(LdapNameBuilder.newInstance("cn=USER,ou=roles").build()))
            .thenReturn(Mockito.mock(DirContextOperations.class));


        usersCtrl.create(request, response);

        JSONObject ret = new JSONObject(response.getContentAsString());
        assertTrue(response.getStatus() == HttpServletResponse.SC_OK);

        assertTrue(ret.getString("uid").equals("ggeoserverprivilegeduser"));
        assertTrue(ret.getString("mail").equals("tomcat@localhost"));
        assertTrue(ret.getString("sn").equals("geoserver privileged user"));
        assertTrue(ret.getString("facsimileTelephoneNumber").equals("+33123456788"));
        assertTrue(ret.getString("street").equals("Avenue des Ducs de Savoie"));
        assertTrue(ret.getString("l").equals("Chambéry"));
        assertTrue(ret.getString("givenName").equals("GS Priv User"));
        assertTrue(ret.getString("postalCode").equals("73000"));
        assertTrue(ret.getString("roomNumber").equals(""));
        assertTrue(ret.getString("telephoneNumber").equals("+331234567890"));
        assertTrue(ret.getString("physicalDeliveryOfficeName").equals(""));
        assertTrue(ret.getString("st").equals(""));
        assertTrue(ret.getString("postOfficeBox").equals("1234"));
        assertTrue(ret.getString("mobile").equals(""));

    }

    @Test
    public void testUpdateUserProtected() throws Exception {

        usersCtrl.update(request, response, "geoserver_privileged_user");

        JSONObject ret = new JSONObject(response.getContentAsString());
        assertTrue(response.getStatus() == HttpServletResponse.SC_CONFLICT);
        assertFalse(ret.getBoolean("success"));
        assertTrue(ret.getString("error").equals("The user is protected, it cannot be updated: geoserver_privileged_user"));
    }

	@Test
	public void testUpdateUserNotFound() throws Exception {

		Mockito.doThrow(NameNotFoundException.class).when(ldapTemplate)
				.lookup(eq(new DistinguishedName("uid=usernotfound,ou=users")), (ContextMapper) Mockito.any());

		usersCtrl.update(request, response, "usernotfound");

		JSONObject ret = new JSONObject(response.getContentAsString());
		assertTrue(response.getStatus() == HttpServletResponse.SC_NOT_FOUND);
		assertFalse(ret.getBoolean("success"));
		assertTrue(ret.getString("error").equals("not_found"));
	}

    @Test
    public void testUpdateUserDataServiceException() throws Exception {

        Mockito.doThrow(DataServiceException.class).when(ldapTemplate)
            .lookup(eq(new DistinguishedName("uid=pmauduit,ou=users")), (ContextMapper) Mockito.any());

        try {
            usersCtrl.update(request, response, "pmauduit");
        } catch (Throwable e) {
            assertTrue(e instanceof IOException);
            assertTrue(response.getStatus() == HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Test
    public void testUpdateDuplicatedEmailException() throws Exception {
        JSONObject reqUsr = new JSONObject().put("mail","tomcat2@localhost");
        request.setContent(reqUsr.toString().getBytes());
        Account fakedAccount = AccountFactory.createBrief("pmauduit", "monkey123", "Pierre",
                "pmauduit", "pmauduit@georchestra.org", "+33123456789",
                "developer & sysadmin", "dev&ops");
        Account fakedAccount2 = AccountFactory.createBrief("pmauduit2", "monkey123", "Pierre",
                "pmauduit", "tomcat2@localhost", "+33123456789",
                "developer & sysadmin", "dev&ops");
        Mockito.doReturn(fakedAccount).when(ldapTemplate).lookup((Name) Mockito.any(), (ContextMapper) Mockito.any());
        // Returns the same account when searching it back
        AndFilter filter = new AndFilter();
        filter.and(new EqualsFilter("objectClass", "inetOrgPerson"));
        filter.and(new EqualsFilter("objectClass", "organizationalPerson"));
        filter.and(new EqualsFilter("objectClass", "person"));
        filter.and(new EqualsFilter("mail", "tomcat2@localhost"));

        List<Account> listFakedAccount = new ArrayList<Account>();
        listFakedAccount.add(fakedAccount2);
        Mockito.doReturn(listFakedAccount).when(ldapTemplate).search(eq(DistinguishedName.EMPTY_PATH),
                eq(filter.encode()), Mockito.any(SearchControls.class), (ContextMapper) Mockito.any());

        Mockito.doReturn(fakedAccount).when(ldapTemplate).lookup(Mockito.any(DistinguishedName.class),
                eq(UserSchema.ATTR_TO_RETRIEVE), (ContextMapper) Mockito.any());

        usersCtrl.update(request, response, "pmauduit");

        JSONObject ret = new JSONObject(response.getContentAsString());
        assertTrue(response.getStatus() == HttpServletResponse.SC_CONFLICT);
        assertFalse(ret.getBoolean("success"));
        assertTrue(ret.getString("error").equals("duplicated_email"));
    }

    @Test
    public void testUpdateDataServiceExceptionWhileModifying() throws Exception {
        JSONObject reqUsr = new JSONObject().put("mail","tomcat2@localhost");
        request.setContent(reqUsr.toString().getBytes());
        Account fakedAccount = AccountFactory.createBrief("pmauduit", "monkey123", "Pierre",
                "pmauduit", "pmauduit@georchestra.org", "+33123456789",
                "developer & sysadmin", "dev&ops");
        String mFilter = "(&(objectClass=inetOrgPerson)(objectClass=organizationalPerson)"
                + "(objectClass=person)(mail=tomcat2@localhost))";
        Mockito.doReturn(fakedAccount).when(ldapTemplate).lookup((Name) Mockito.any(), eq(UserSchema.ATTR_TO_RETRIEVE), (ContextMapper) Mockito.any());
        Mockito.doThrow(DataServiceException.class).when(ldapTemplate).search(eq(DistinguishedName.EMPTY_PATH),
                eq(mFilter),(SearchControls) Mockito.any(), (ContextMapper) Mockito.any());
        
        

        try {
            usersCtrl.update(request, response, "pmauduit");
        } catch (Throwable e) {
            assertTrue(e instanceof IOException);
            assertTrue(response.getStatus() ==  HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Test
    public void testUpdateBadJSON() throws Exception {
        request.setContent("{[this is ] } not valid JSON obviously ....".getBytes());
        Mockito.when(ldapTemplate.lookup(Mockito.any(Name.class), Mockito.any(ContextMapper.class))).thenReturn(
              AccountFactory.createBrief("pmauduit", "monkey123", "Pierre", "Mauduit",
              "pmt@c2c.com", "+123", "developer", "developer"));
        try {
            usersCtrl.update(request, response, "pmauduit");
        } catch (Throwable e) {
            assertTrue(e instanceof IOException);
            JSONObject ret = new JSONObject(response.getContentAsString());
            assertFalse(ret.getBoolean("success"));
            assertTrue(ret.getString("error").equals("params_not_understood"));
        }

    }

    @Test
    public void testUpdate() throws Exception {
        JSONObject reqUsr = new JSONObject().put("sn","newPmauduit")
                .put("postalAddress", "newAddress")
                .put("postOfficeBox", "newPOBox")
                .put("postalCode", "73000")
                .put("street", "newStreet")
                .put("l", "newLocality") // locality
                .put("telephoneNumber", "+33987654321")
                .put("facsimileTelephoneNumber", "+339182736745")
                .put("title", "CEO")
                .put("description", "CEO geOrchestra Corporation")
                .put("givenName", "newPierre");
        request.setContent(reqUsr.toString().getBytes());
        Account fakedAccount = AccountFactory.createBrief("pmauduit", "monkey123", "Pierre",
                "pmauduit", "pmauduit@georchestra.org", "+33123456789",
                "developer & sysadmin", "dev&ops");
        Mockito.doReturn(fakedAccount).when(ldapTemplate).lookup((Name) Mockito.any(), (String[]) Mockito.any(), (ContextMapper) Mockito.any());
        // Returns the same account when searching it back
        String mFilter = "(&(objectClass=inetOrgPerson)(objectClass=organizationalPerson)"
                + "(objectClass=person)(mail=tomcat2@localhost))";
        List<Account> listFakedAccount = new ArrayList<Account>();
        listFakedAccount.add(fakedAccount);
        Mockito.doReturn(listFakedAccount).when(ldapTemplate).search(eq(DistinguishedName.EMPTY_PATH),
                eq(mFilter), (SearchControls) Mockito.any(), (ContextMapper) Mockito.any());
        Mockito.doReturn(Mockito.mock(DirContextOperations.class)).when(ldapTemplate).lookupContext((Name) Mockito.any());

        usersCtrl.update(request, response, "pmauduit");

        JSONObject ret = new JSONObject(response.getContentAsString());
        assertTrue(response.getStatus() == HttpServletResponse.SC_OK);

        // Add missing param in request
        reqUsr.put("mail", "pmauduit@georchestra.org");
        reqUsr.put("cn", "newPierre newPmauduit");
        reqUsr.put("uid", "pmauduit");
        reqUsr.put("org", "");
        
        assertTrue(UsersControllerTest.jsonEquals(reqUsr, ret));

    }

    @Test
    public void testUpdateEmptyTelephoneNumber() throws Exception {
        JSONObject reqUsr = new JSONObject().put("sn","newPmauduit")
                .put("postalAddress", "newAddress")
                .put("postOfficeBox", "newPOBox")
                .put("postalCode", "73000")
                .put("street", "newStreet")
                .put("l", "newLocality") // locality
                .put("telephoneNumber", "")
                .put("facsimileTelephoneNumber", "+339182736745")
                .put("title", "CEO")
                .put("description", "CEO geOrchestra Corporation")
                .put("givenName", "newPierre");
        request.setContent(reqUsr.toString().getBytes());
        Account fakedAccount = AccountFactory.createBrief("pmauduit", "monkey123", "Pierre",
                "pmauduit", "pmauduit@georchestra.org", "+33123456789",
                "developer & sysadmin", "dev&ops");
        Mockito.doReturn(fakedAccount).when(ldapTemplate).
            lookup((Name) Mockito.any(), eq(UserSchema.ATTR_TO_RETRIEVE), (ContextMapper) Mockito.any());
        // Returns the same account when searching it back
        AndFilter filter = new AndFilter();
        filter.and(new EqualsFilter("objectClass", "inetOrgPerson"));
        filter.and(new EqualsFilter("objectClass", "organizationalPerson"));
        filter.and(new EqualsFilter("objectClass", "person"));
        filter.and(new EqualsFilter("mail", "tomcat2@localhost"));


        List<Account> listFakedAccount = new ArrayList<Account>();
        listFakedAccount.add(fakedAccount);
        Mockito.doReturn(listFakedAccount).when(ldapTemplate).search(eq(DistinguishedName.EMPTY_PATH),
                eq(filter.encode()), Mockito.any(SearchControls.class), (ContextMapper) Mockito.any());
        Mockito.doReturn(Mockito.mock(DirContextOperations.class)).
            when(ldapTemplate).lookupContext((Name) Mockito.any());

        usersCtrl.update(request, response, "pmauduit");

        JSONObject ret = new JSONObject(response.getContentAsString());
        assertTrue(response.getStatus() == HttpServletResponse.SC_OK);

        // Add missing param in request
        reqUsr.put("mail", "pmauduit@georchestra.org");
        reqUsr.put("cn", "newPierre newPmauduit");
        reqUsr.put("uid", "pmauduit");
        reqUsr.put("org", "");

        assertTrue(UsersControllerTest.jsonEquals(reqUsr, ret));

    }

    @Test
    public void testDeleteUserProtected() throws Exception {
        usersCtrl.delete("geoserver_privileged_user", request, response);

        JSONObject ret = new JSONObject(response.getContentAsString());
        assertTrue(response.getStatus() == HttpServletResponse.SC_CONFLICT);
        assertFalse(ret.getBoolean("success"));
        assertTrue(ret.getString("error").equals("The user is protected, it cannot be deleted: geoserver_privileged_user"));

    }

    @Test
    public void testDeleteDataServiceExceptionCaught() throws Exception {
        Mockito.doThrow(DataServiceException.class).when(ldapTemplate).unbind((Name) Mockito.any(), eq(true));
        boolean caught = false;


        try {
            usersCtrl.delete("pmauduit", request, response);
        } catch (Throwable e) {
            caught = true;
            assertTrue(e instanceof IOException);
            assertTrue(response.getStatus() == HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        assertTrue(caught);
    }

    @Test
    public void testDeleteNotFoundExceptionCaught() throws Exception {
        Mockito.doThrow(NameNotFoundException.class).when(ldapTemplate).unbind((Name) Mockito.any(), eq(true));

        usersCtrl.delete("pmauduitnotfound", request, response);

        JSONObject ret = new JSONObject(response.getContentAsString());
        assertTrue(response.getStatus() == HttpServletResponse.SC_NOT_FOUND);
        assertFalse(ret.getBoolean("success"));
        assertTrue(ret.getString("error").equals("not_found"));
    }
    

    @Test
    public void testResquestProducesDelete() throws Exception {
        usersCtrl.delete("pmaudui", request, response);
        assertTrue(response.getContentType().equals("application/json"));
    }


    private static boolean jsonEquals(JSONObject a, JSONObject b){

        Iterator<String> aIt = a.sortedKeys();
        Set<String> aKeys = new HashSet<String>();
        while(aIt.hasNext()){
            String key = aIt.next();
            aKeys.add(key);
            String aValue = null;
            String bValue = null;
            try {
                aValue = a.getString(key);
                bValue = b.getString(key);
            } catch (JSONException e) {
                return false;
            }

            if(bValue == null)
                return false;
            if(!aValue.equals(bValue))
                return false;
        }
        Iterator<String> bIt = b.sortedKeys();
        Set<String> bKeys = new HashSet<String>();
        while(bIt.hasNext()) {
            String key = bIt.next();
            bKeys.add(key);
        }
        if(bKeys.size() != aKeys.size())
            return false;

        return true;
    }
}
