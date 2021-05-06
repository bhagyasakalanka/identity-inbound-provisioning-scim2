/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.scim2.common.utils;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.scim2.common.exceptions.IdentitySCIMException;
import org.wso2.carbon.identity.scim2.common.group.SCIMGroupHandler;
import org.wso2.carbon.identity.scim2.common.internal.SCIMCommonComponentHolder;
import org.wso2.carbon.stratos.common.util.ClaimsMgtUtil;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.api.UserRealm;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.UserStoreManager;
import org.wso2.carbon.user.core.common.AbstractUserStoreManager;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.user.core.tenant.TenantManager;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;
import static org.testng.Assert.assertNull;

@PrepareForTest({SCIMCommonComponentHolder.class, ClaimsMgtUtil.class, IdentityTenantUtil.class,
        IdentityUtil.class, SCIMCommonUtils.class, AdminAttributeUtil.class})
public class AdminAttributeUtilTest extends PowerMockTestCase {

    private static final List<Integer> INVALID_TENANT_IDS = Arrays.asList(-1, -2, -3);

    @Mock
    RealmService realmService;

    @Mock
    UserRealm userRealm;

    @Mock
    UserStoreManager userStoreManager;

    @Mock
    AbstractUserStoreManager abstractUserStoreManager;

    @Mock
    SCIMGroupHandler scimGroupHandler;

    @Mock
    TenantManager tenantManager;

    @BeforeMethod
    public void setUp() throws Exception {

        initMocks(this);
        mockStatic(SCIMCommonComponentHolder.class);
        mockStatic(ClaimsMgtUtil.class);
        mockStatic(IdentityTenantUtil.class);
        mockStatic(SCIMCommonUtils.class);
        when(SCIMCommonComponentHolder.getRealmService()).thenReturn(realmService);
        when(realmService.getTenantUserRealm(anyInt())).thenReturn(userRealm);
        when(realmService.getTenantManager()).thenReturn(tenantManager);
        when(ClaimsMgtUtil.getAdminUserNameFromTenantId(any(RealmService.class), anyInt())).
                thenAnswer(invocationOnMock -> {
            int tenantIdArg = invocationOnMock.getArgumentAt(1, Integer.class);
            if (tenantIdArg == -1234) {
                return "Boostrap Admin";
            }
            if (INVALID_TENANT_IDS.contains(tenantIdArg)) {
                String msg = "Unable to retrieve the admin name for the tenant with the tenant Id: " + tenantIdArg;
                throw new Exception(msg, new UserStoreException());
            }
            return "admin";
        });
    }

    @DataProvider(name = "dataProviderForUpdateAdminUserData")
    public Object[][] dataProviderForUpdateAdminUserData() {

        return new Object[][]{
                {1, false, true, "scimId", 0},
                {1, true, false, "scimId", 1},
                {1, true, true, "", 1},
                {1, true, true, "scimId", 0},
                {-1234, true, true, "scimId", 0},
                {-1, true, true, "scimId", 0},
        };
    }

    @Test(dataProvider = "dataProviderForUpdateAdminUserData")
    public void testUpdateAdminUser(int tenantId, boolean isSCIMEnabled, boolean validateSCIMID, String scimID,
                                    int times) throws Exception {

        when(userRealm.getUserStoreManager()).thenReturn(userStoreManager);
        when(userStoreManager.isSCIMEnabled()).thenReturn(isSCIMEnabled);
        when(userStoreManager.getUserClaimValue(anyString(), anyString(), anyString())).thenReturn(scimID);
        doAnswer(invocationOnMock -> null).when(userStoreManager).setUserClaimValues(anyString(),
                anyMapOf(String.class, String.class), anyString());
        AdminAttributeUtil.updateAdminUser(tenantId, validateSCIMID);
        verify(userStoreManager, times(times)).setUserClaimValues(anyString(), anyMapOf(String.class, String.class),
                anyString());
    }

    @DataProvider(name = "dataProviderForUpdateAdminGroup")
    public Object[][] dataProviderForUpdateAdminGroup() {

        RealmConfiguration realmConfiguration = new RealmConfiguration();
        Map<String, String> userStoreProperties = new HashMap<>();
        userStoreProperties.put("DomainName", "carbon");
        realmConfiguration.setAdminRoleName("admin");
        realmConfiguration.setUserStoreProperties(userStoreProperties);
        RealmConfiguration realmConfigurationNoDomainName = new RealmConfiguration();
        Map<String, String> userStorePropertiesDomainName = new HashMap<>();
        userStoreProperties.put("InvalidDomainName", "carbon2");
        realmConfigurationNoDomainName.setUserStoreProperties(userStorePropertiesDomainName);
        realmConfigurationNoDomainName.setAdminRoleName("admin");
        return new Object[][]{
                {true, true, true, true, 1, realmConfiguration, 0},
                {true, true, true, false, 1, realmConfiguration, 0},
                {true, true, false, true, 1, realmConfiguration, 2},
                {true, true, false, false, 1, realmConfiguration, 1},
                {true, false, true, true, 1, realmConfigurationNoDomainName, 0},
                {true, false, true, false, 1, realmConfigurationNoDomainName, 0},
                {true, false, false, true, 1, realmConfigurationNoDomainName, 1},
                {true, false, false, false, 1, realmConfigurationNoDomainName, 1},
                {false, true, false, true, 1, realmConfigurationNoDomainName, 0},
        };
    }

    @Test(dataProvider = "dataProviderForUpdateAdminGroup")
    public void testUpdateAdminGroup(boolean isSCIMEnabled, boolean isSeparationEnabled, boolean isGroupExisting,
                                     boolean isExistingRoleId, int tenantId, Object realmConfiguration, int times)
            throws Exception {

        mockStatic(IdentityUtil.class);
        when(userRealm.getUserStoreManager()).thenReturn(abstractUserStoreManager);
        when(abstractUserStoreManager.isSCIMEnabled()).thenReturn(isSCIMEnabled);
        when(abstractUserStoreManager.getTenantId()).thenReturn(tenantId);
        whenNew(SCIMGroupHandler.class).withAnyArguments().thenReturn(scimGroupHandler);
        when(scimGroupHandler.isGroupExisting(anyString())).thenReturn(isGroupExisting);
        when(abstractUserStoreManager.isRoleAndGroupSeparationEnabled()).thenReturn(isSeparationEnabled);
        when(abstractUserStoreManager.isExistingRole(anyString())).thenReturn(isExistingRoleId);
        doAnswer(invocationOnMock -> null).when(scimGroupHandler).addMandatoryAttributes(anyString());
        when(abstractUserStoreManager.getRealmConfiguration()).thenReturn((RealmConfiguration) realmConfiguration);
        when(IdentityUtil.getPrimaryDomainName()).thenReturn("PRIMARY");
        AdminAttributeUtil.updateAdminGroup(tenantId);
        verify(scimGroupHandler, Mockito.times(times)).addMandatoryAttributes(anyString());
    }

    @DataProvider(name = "dataProviderForUpdateAdminGroupThrowingError")
    public Object[][] dataProviderForUpdateAdminGroupThrowingError() {

        RealmConfiguration realmConfiguration = new RealmConfiguration();
        Map<String, String> userStoreProperties = new HashMap<>();
        userStoreProperties.put("DomainName", "carbon");
        realmConfiguration.setAdminRoleName("admin");
        realmConfiguration.setUserStoreProperties(userStoreProperties);
        return new Object[][]{
                {true, true, realmConfiguration},
                {false, true, realmConfiguration},
                {false, false, realmConfiguration},
        };
    }

    @Test(dataProvider = "dataProviderForUpdateAdminGroupThrowingError")
    public void testProviderForUpdateAdminGroupThrowingError(boolean isUserStoreManagerError,
                                                             boolean isSCIMEnabledError, Object realmConfiguration)
            throws Exception {

        mockStatic(IdentityUtil.class);
        when(userRealm.getUserStoreManager()).thenAnswer(invocationOnMock -> {
            if (isUserStoreManagerError) {
                throw new UserStoreException();
            }
            return abstractUserStoreManager;
        });
        when(abstractUserStoreManager.isSCIMEnabled()).thenAnswer(invocationOnMock -> {
            if (isSCIMEnabledError) {
                throw new UserStoreException();
            }
            return true;
        });
        when(abstractUserStoreManager.getTenantId()).thenReturn(1);
        whenNew(SCIMGroupHandler.class).withAnyArguments().thenReturn(scimGroupHandler);
        when(scimGroupHandler.isGroupExisting(anyString())).
                thenThrow(new IdentitySCIMException(
                        "Error when reading the group information from the persistence store.",
                        new SQLException()));
        when(abstractUserStoreManager.getRealmConfiguration()).thenReturn((RealmConfiguration) realmConfiguration);
        when(IdentityUtil.getPrimaryDomainName()).thenReturn("PRIMARY");
        AdminAttributeUtil.updateAdminGroup(1);
        assertNull(null);
    }
}
