/*
 *
 * Copyright 2018. Gatekeeper Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.finra.gatekeeper.services.accessrequest;

import org.activiti.engine.HistoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricVariableInstance;
import org.activiti.engine.history.HistoricVariableInstanceQuery;
import org.activiti.engine.history.NativeHistoricVariableInstanceQuery;
import org.activiti.engine.impl.persistence.entity.HistoricVariableInstanceEntity;
import org.activiti.engine.impl.persistence.entity.VariableInstance;
import org.activiti.engine.runtime.ProcessInstanceQuery;
import org.activiti.engine.task.Task;
import org.activiti.engine.task.TaskQuery;
import org.finra.gatekeeper.common.authfilter.parser.IGatekeeperUserProfile;
import org.finra.gatekeeper.common.services.user.model.GatekeeperUserEntry;
import org.finra.gatekeeper.configuration.GatekeeperApprovalProperties;
import org.finra.gatekeeper.configuration.GatekeeperOverrideProperties;
import org.finra.gatekeeper.configuration.GatekeeperProperties;
import org.finra.gatekeeper.controllers.wrappers.AccessRequestWrapper;
import org.finra.gatekeeper.controllers.wrappers.ActiveAccessRequestWrapper;
import org.finra.gatekeeper.controllers.wrappers.CompletedAccessRequestWrapper;
import org.finra.gatekeeper.exception.GatekeeperException;
import org.finra.gatekeeper.services.accessrequest.model.*;
import org.finra.gatekeeper.services.accessrequest.model.response.AccessRequestCreationResponse;
import org.finra.gatekeeper.common.services.account.AccountInformationService;
import org.finra.gatekeeper.common.services.account.model.Account;
import org.finra.gatekeeper.common.services.account.model.Region;
import org.finra.gatekeeper.services.db.DatabaseConnectionService;
import org.finra.gatekeeper.services.auth.GatekeeperRoleService;
import org.finra.gatekeeper.services.auth.GatekeeperRdsRole;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for the Gatekeeper RDS Access Request Service
 */
@RunWith(MockitoJUnitRunner.class)
public class AccessRequestServiceTest {

    @InjectMocks
    private AccessRequestService accessRequestService;

    @Mock
    private TaskService taskService;

    @Mock
    private AccessRequestRepository accessRequestRepository;

    @Mock
    private GatekeeperRoleService gatekeeperRoleService;

    @Mock
    private HistoryService historyService;

    @Mock
    private AccessRequest ownerRequest;

    @Mock
    private AccessRequestWrapper ownerRequestWrapper;

    @Mock
    private AccessRequest nonOwnerRequest;

    @Mock
    private AccessRequest adminRequest;

    @Mock
    private AWSRdsDatabase awsRdsDatabase;

    @Mock
    private User user;

    @Mock
    private GatekeeperUserEntry userEntry;

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private ProcessInstanceQuery processInstanceQuery;

    @Mock
    private HistoricVariableInstanceQuery historicVariableInstanceQuery;

    @Mock
    private NativeHistoricVariableInstanceQuery nativeHistoricVariableInstanceQuery;

    @Mock
    private HistoricVariableInstanceEntity ownerHistoricVariableInstanceAttempt;

    @Mock
    private HistoricVariableInstanceEntity ownerHistoricVariableInstanceStatus;

    @Mock
    private HistoricVariableInstanceEntity ownerHistoricVariableInstanceAccessRequest;

    @Mock
    private HistoricVariableInstanceEntity nonOwnerHistoricVariableInstanceAttempt;

    @Mock
    private HistoricVariableInstanceEntity nonOwnerHistoricVariableInstanceStatus;

    @Mock
    private HistoricVariableInstanceEntity nonOwnerHistoricVariableInstanceAccessRequest;

    @Mock
    private VariableInstance ownerOneTaskInstance;

    @Mock
    private VariableInstance ownerTwoTaskInstance;


    @Mock
    private GatekeeperOverrideProperties overridePolicy;

    @Mock
    private Task ownerOneTask;

    @Mock
    private Task ownerTwoTask;

    @Mock
    private TaskQuery taskQuery;

    @Mock
    private AccountInformationService accountInformationService;

    @Mock
    private DatabaseConnectionService databaseConnectionService;

    private Date testDate;

    @Mock
    private GatekeeperProperties gatekeeperProperties;

    @Mock
    private GatekeeperApprovalProperties approvalThreshold;

    @Before
    public void initMocks() {
        testDate = new Date();
        Integer mockMaximum = 180;
        //Setting up the spring values
        Map<String, Map<String, Integer>> mockDev = new HashMap<>();
        Map<String, Integer> mockDba = new HashMap<>();
        mockDba.put("dev",180);
        mockDba.put("qa",180);
        mockDba.put("prod",180);
        mockDev.put("datafix", mockDba);

        Region[] regions = new Region[]{ new Region("us-east-1") };
        Account mockAccount = new Account("1234", "Dev Test", "dev", "dev-test", Arrays.asList(regions));

        when(approvalThreshold.getApprovalPolicy(GatekeeperRdsRole.DEV)).thenReturn(mockDev);
        when(approvalThreshold.getApprovalPolicy(GatekeeperRdsRole.OPS)).thenReturn(mockDev);
        when(approvalThreshold.getApprovalPolicy(GatekeeperRdsRole.DBA)).thenReturn(mockDev);

        when(overridePolicy.getMaxDaysForRequest(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(mockMaximum);

        List<AWSRdsDatabase> instances = new ArrayList<>();
        when(awsRdsDatabase.getApplication()).thenReturn("TestApplication");
        when(awsRdsDatabase.getInstanceId()).thenReturn("testId");
        when(awsRdsDatabase.getDbName()).thenReturn("testDbName");
        when(awsRdsDatabase.getEndpoint()).thenReturn("testEndpoint");
        when(awsRdsDatabase.getEngine()).thenReturn("testEngine");
        when(awsRdsDatabase.getStatus()).thenReturn("UP");
        instances.add(awsRdsDatabase);

        //Owner mock
        when(ownerRequest.getAccount()).thenReturn("DEV");
        when(ownerRequest.getAwsRdsInstances()).thenReturn(instances);
        when(ownerRequest.getDays()).thenReturn(1);
        when(ownerRequest.getRequestorId()).thenReturn("owner");
        when(ownerRequest.getId()).thenReturn(1L);
        when(ownerRequest.getAccountSdlc()).thenReturn("dev");



        //Non-owner mock
        when(nonOwnerRequest.getAccount()).thenReturn("DEV");
        when(nonOwnerRequest.getAwsRdsInstances()).thenReturn(instances);
        when(nonOwnerRequest.getDays()).thenReturn(1);
        when(nonOwnerRequest.getRequestorId()).thenReturn("non-owner");
        when(nonOwnerRequest.getId()).thenReturn(2L);
        when(nonOwnerRequest.getAccountSdlc()).thenReturn("dev");

        Set<String> ownerMemberships = new HashSet<String>();
        ownerMemberships.add("TestApplication");

        List<UserRole> roles = new ArrayList<>();
        UserRole userRole = new UserRole();
        userRole.setRole("datafix");
        roles.add(userRole);
        when(nonOwnerRequest.getRoles()).thenReturn(roles);
        when(ownerRequest.getRoles()).thenReturn(roles);

        when(ownerRequestWrapper.getInstances()).thenReturn(instances);
        when(ownerRequestWrapper.getDays()).thenReturn(1);
        when(ownerRequestWrapper.getRequestorId()).thenReturn("owner");
        when(ownerRequestWrapper.getAccount()).thenReturn("testAccount");
        when(ownerRequestWrapper.getRegion()).thenReturn("testRegion");
        when(ownerRequestWrapper.getAccountSdlc()).thenReturn("dev");
        when(userEntry.getUserId()).thenReturn("testUserId");
        when(userEntry.getName()).thenReturn("testName");
        when(userEntry.getEmail()).thenReturn("testEmail@finra.org");
        when(user.getUserId()).thenReturn("testUserId");
        List<User> users = new ArrayList<>();
        users.add(user);
        when(ownerRequestWrapper.getUsers()).thenReturn(users);
        when(ownerRequest.getUsers()).thenReturn(users);

        when(gatekeeperRoleService.getRole()).thenReturn(GatekeeperRdsRole.DEV);
        when(gatekeeperRoleService.getUserProfile()).thenReturn(userEntry);

        when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(runtimeService.createProcessInstanceQuery().count()).thenReturn(2L);


        //Mocks for getActiveRequest()
        when(ownerOneTask.getExecutionId()).thenReturn("ownerOneTask");
        when(ownerOneTask.getCreateTime()).thenReturn(new Date(4500000));
        when(ownerOneTask.getId()).thenReturn("taskOne");

        when(ownerTwoTask.getExecutionId()).thenReturn("ownerTwoTask");
        when(ownerTwoTask.getCreateTime()).thenReturn(testDate);
        when(ownerTwoTask.getId()).thenReturn("taskTwo");

        when(ownerOneTaskInstance.getTextValue2()).thenReturn("1");
        when(ownerTwoTaskInstance.getTextValue2()).thenReturn("2");

        when(accessRequestRepository.findOne(1L)).thenReturn(ownerRequest);
        when(accessRequestRepository.findOne(2L)).thenReturn(nonOwnerRequest);

        when(runtimeService.getVariableInstance("ownerOneTask", "accessRequest")).thenReturn(ownerOneTaskInstance);
        when(runtimeService.getVariableInstance("ownerTwoTask", "accessRequest")).thenReturn(ownerTwoTaskInstance);


        List<Task> activeTasks = new ArrayList<>();
        activeTasks.add(ownerOneTask);
        activeTasks.add(ownerTwoTask);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskService.createTaskQuery().active()).thenReturn(taskQuery);
        when(taskService.createTaskQuery().active().list()).thenReturn(activeTasks);

        //Mocks for getCompletedRequest()
        List<HistoricVariableInstance> taskVars = new ArrayList<>();
        when(ownerHistoricVariableInstanceAttempt.getProcessInstanceId()).thenReturn("ownerRequest");
        when(ownerHistoricVariableInstanceStatus.getProcessInstanceId()).thenReturn("ownerRequest");
        when(ownerHistoricVariableInstanceAccessRequest.getProcessInstanceId()).thenReturn("ownerRequest");

        when(nonOwnerHistoricVariableInstanceAttempt.getProcessInstanceId()).thenReturn("nonOwnerRequest");
        when(nonOwnerHistoricVariableInstanceStatus.getProcessInstanceId()).thenReturn("nonOwnerRequest");
        when(nonOwnerHistoricVariableInstanceAccessRequest.getProcessInstanceId()).thenReturn("nonOwnerRequest");


        when(ownerHistoricVariableInstanceAttempt.getValue()).thenReturn(1);
        when(ownerHistoricVariableInstanceAttempt.getVariableName()).thenReturn("attempts");
        when(ownerHistoricVariableInstanceAttempt.getCreateTime()).thenReturn(new Date(45000));
        when(ownerHistoricVariableInstanceAttempt.getTextValue2()).thenReturn("1");
        when(ownerHistoricVariableInstanceStatus.getValue()).thenReturn("APPROVAL_GRANTED");
        when(ownerHistoricVariableInstanceStatus.getVariableName()).thenReturn("requestStatus");
        when(ownerHistoricVariableInstanceStatus.getLastUpdatedTime()).thenReturn(new Date(45002));
        when(ownerHistoricVariableInstanceStatus.getTextValue2()).thenReturn("1");
        when(ownerHistoricVariableInstanceAccessRequest.getValue()).thenReturn(ownerRequest);
        when(ownerHistoricVariableInstanceAccessRequest.getVariableName()).thenReturn("accessRequest");
        when(ownerHistoricVariableInstanceAccessRequest.getCreateTime()).thenReturn(new Date(45000));
        when(ownerHistoricVariableInstanceAccessRequest.getTextValue2()).thenReturn("1");
        when(ownerHistoricVariableInstanceAccessRequest.getLastUpdatedTime()).thenReturn(new Date(45002));

        when(nonOwnerHistoricVariableInstanceAttempt.getValue()).thenReturn(2);
        when(nonOwnerHistoricVariableInstanceAttempt.getVariableName()).thenReturn("attempts");
        when(nonOwnerHistoricVariableInstanceAttempt.getCreateTime()).thenReturn(new Date(45002));
        when(nonOwnerHistoricVariableInstanceAttempt.getTextValue2()).thenReturn("2");
        when(nonOwnerHistoricVariableInstanceStatus.getValue()).thenReturn(null);
        when(nonOwnerHistoricVariableInstanceStatus.getVariableName()).thenReturn("requestStatus");
        when(nonOwnerHistoricVariableInstanceStatus.getLastUpdatedTime()).thenReturn(new Date(45003));
        when(nonOwnerHistoricVariableInstanceStatus.getTextValue2()).thenReturn("2");
        when(nonOwnerHistoricVariableInstanceAccessRequest.getValue()).thenReturn(nonOwnerRequest);
        when(nonOwnerHistoricVariableInstanceAccessRequest.getVariableName()).thenReturn("accessRequest");
        when(nonOwnerHistoricVariableInstanceAccessRequest.getCreateTime()).thenReturn(new Date(45002));
        when(nonOwnerHistoricVariableInstanceAccessRequest.getTextValue2()).thenReturn("2");
        when(nonOwnerHistoricVariableInstanceAccessRequest.getLastUpdatedTime()).thenReturn(new Date(45003));

        taskVars.add(ownerHistoricVariableInstanceAttempt);
        taskVars.add(ownerHistoricVariableInstanceStatus);
        taskVars.add(ownerHistoricVariableInstanceAccessRequest);

        taskVars.add(nonOwnerHistoricVariableInstanceAttempt);
        taskVars.add(nonOwnerHistoricVariableInstanceStatus);
        taskVars.add(nonOwnerHistoricVariableInstanceAccessRequest);

        when(historyService.createHistoricVariableInstanceQuery()).thenReturn(historicVariableInstanceQuery);
        when(historyService.createHistoricVariableInstanceQuery().list()).thenReturn(taskVars);
        when(historicVariableInstanceQuery.excludeVariableInitialization()).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.variableName(Mockito.any())).thenReturn(historicVariableInstanceQuery);
        when(historyService.createNativeHistoricVariableInstanceQuery()).thenReturn(nativeHistoricVariableInstanceQuery);
        when(nativeHistoricVariableInstanceQuery.sql(Mockito.any())).thenReturn(nativeHistoricVariableInstanceQuery);
        when(nativeHistoricVariableInstanceQuery.list()).thenReturn(taskVars);
        Map<String,String> statusMap = new HashMap<>();
        statusMap.put("testId","Unknown");

        when(accountInformationService.getAccountByAlias(any())).thenReturn(mockAccount);
        when(accessRequestRepository.findAll(Mockito.anyList())).thenReturn(Arrays.asList(ownerRequest, nonOwnerRequest));
    }


    /**
     * Test the command used within the workflow to determine whether or not
     * approval is needed.
     */
    @Test
    public void testApprovalNeededAdmin() throws Exception {
        when(gatekeeperRoleService.getRole()).thenReturn(GatekeeperRdsRole.APPROVER);
        Assert.assertFalse(accessRequestService.isApprovalNeeded(ownerRequest));
        Assert.assertFalse(accessRequestService.isApprovalNeeded(nonOwnerRequest));
    }

    /**
     * Test the command used within the workflow to determine whether or not
     * approval is needed when the user has DEV role, is owner of instance, and
     * does exceed threshold
     */
    @Test
    public void testApprovalNeededDevOwnerThreshold() throws Exception {
        when(ownerRequest.getDays()).thenReturn(300);
        Assert.assertTrue(accessRequestService.isApprovalNeeded(ownerRequest));
    }

    /**
     * Test the command used within the workflow to determine whether or not
     * approval is needed when the user has DEV role, is owner of instance, and
     * does not exceed threshold
     */
    @Test
    public void testApprovalNeededDevOwner() throws Exception {
        Map<String,Set<String>> memberships = new HashMap<>();
        Set<String> sdlcSet = new HashSet<>();
        sdlcSet.add("DEV");
        memberships.put("TestApplication",sdlcSet);
        when(gatekeeperRoleService.getDevMemberships(ownerRequest.getRequestorId())).thenReturn(memberships);
        when(ownerRequest.getDays()).thenReturn(179);
        Assert.assertFalse(accessRequestService.isApprovalNeeded(ownerRequest));
    }

    /**
     * Test the command used within the workflow to determine whether or not
     * approval is needed when the user has DEV role, is not owner of instance, and
     * does not exceed threshold
     */
    @Test
    public void testApprovalNeededDevNonOwner() throws Exception {
        Map<String,Set<String>> memberships = new HashMap<>();
        Set<String> sdlcSet = new HashSet<>();
        sdlcSet.add("QA");
        memberships.put("TestApplication",sdlcSet);
        when(gatekeeperRoleService.getDevMemberships(nonOwnerRequest.getRequestorId())).thenReturn(memberships);
        when(nonOwnerRequest.getDays()).thenReturn(179);

        Assert.assertTrue(accessRequestService.isApprovalNeeded(nonOwnerRequest));
    }

    /**
     * Test the command used within the workflow to determine whether or not
     * approval is needed when the user has DEV role, is not owner of instance, and
     * does exceed threshold
     */
    @Test
    public void testApprovalNeededDevThreshold() throws Exception {
        when(nonOwnerRequest.getDays()).thenReturn(300);
        Assert.assertTrue(accessRequestService.isApprovalNeeded(nonOwnerRequest));
    }


    /**
     * Test the command used within the workflow to determine whether or not
     * approval is needed when the user has OPS role, is owner of instance, and
     * does exceed threshold
     */
    @Test
    public void testApprovalNeededOpsOwnerThreshold() throws Exception {
        when(ownerRequest.getDays()).thenReturn(181);
        when(gatekeeperRoleService.getRole()).thenReturn(GatekeeperRdsRole.OPS);
        Assert.assertTrue(accessRequestService.isApprovalNeeded(ownerRequest));
    }

    /**
     * Test the command used within the workflow to determine whether or not
     * approval is needed when the user has OPS role, is owner of instance, and
     * does not exceed threshold
     */
    @Test
    public void testApprovalNeededOpsOwner() throws Exception {
        when(gatekeeperRoleService.getRole()).thenReturn(GatekeeperRdsRole.OPS);
        Assert.assertFalse(accessRequestService.isApprovalNeeded(ownerRequest));
    }

    /**
     * Test the command used within the workflow to determine whether or not
     * approval is needed when the user has OPS role, is not owner of instance, and
     * does not exceed threshold
     */
    @Test
    public void testApprovalNeededOpsNonOwner() throws Exception {
        when(gatekeeperRoleService.getRole()).thenReturn(GatekeeperRdsRole.OPS);
        when(nonOwnerRequest.getDays()).thenReturn(179);
        Assert.assertFalse(accessRequestService.isApprovalNeeded(nonOwnerRequest));
    }

    /**
     * Test the command used within the workflow to determine whether or not
     * approval is needed when the user has OPS role, is not owner of instance, and
     * does exceed threshold
     */
    @Test
    public void testApprovalNeededOpsThreshold() throws Exception {
        when(gatekeeperRoleService.getRole()).thenReturn(GatekeeperRdsRole.OPS);
        when(nonOwnerRequest.getDays()).thenReturn(181);
        Assert.assertTrue(accessRequestService.isApprovalNeeded(nonOwnerRequest));
    }

    /**
     * Test the command used within the workflow to determine whether or not
     * approval is needed when the user has SUPPORT role, is owner of instance, and
     * does exceed threshold
     */
    @Test
    public void testApprovalNeededSupportOwnerThreshold() throws Exception {
        when(ownerRequest.getDays()).thenReturn(181);
        when(gatekeeperRoleService.getRole()).thenReturn(GatekeeperRdsRole.DBA);
        Assert.assertTrue(accessRequestService.isApprovalNeeded(ownerRequest));
    }

    /**
     * Test the command used within the workflow to determine whether or not
     * approval is needed when the user has SUPPORT role, is owner of instance, and
     * does not exceed threshold
     */
    @Test
    public void testApprovalNeededSupportOwner() throws Exception {
        when(gatekeeperRoleService.getRole()).thenReturn(GatekeeperRdsRole.DBA);
        when(ownerRequest.getDays()).thenReturn(179);
        Set<String> memberships = new HashSet<>();
        memberships.add("TestApplication");
        when(gatekeeperRoleService.getDbaMemberships(ownerRequest.getRequestorId())).thenReturn(memberships);
        Assert.assertFalse(accessRequestService.isApprovalNeeded(ownerRequest));
    }

    /**
     * Test the command used within the workflow to determine whether or not
     * approval is needed when the user has DBA role, is not owner of instance, and
     * does not exceed threshold
     */
    @Test
    public void testApprovalNeededSupportNonOwner() throws Exception {
        when(gatekeeperRoleService.getRole()).thenReturn(GatekeeperRdsRole.DBA);
        when(nonOwnerRequest.getDays()).thenReturn(179);
        Assert.assertTrue(accessRequestService.isApprovalNeeded(nonOwnerRequest));
    }

    /**
     * Test the command used within the workflow to determine whether or not
     * approval is needed when the user has SUPPORT role, is not owner of instance, and
     * does exceed threshold
     */
    @Test
    public void testApprovalNeededSupportThreshold() throws Exception {
        when(gatekeeperRoleService.getRole()).thenReturn(GatekeeperRdsRole.DBA);
        when(nonOwnerRequest.getDays()).thenReturn(181);
        Set<String> memberships = new HashSet<>();
        memberships.add("Test");
        when(gatekeeperRoleService.getDbaMemberships(nonOwnerRequest.getRequestorId())).thenReturn(memberships);
        Assert.assertTrue(accessRequestService.isApprovalNeeded(nonOwnerRequest));
    }

    /**
     * Test for making sure the storeAccessRequest method works. Makes sure the accessRequestRepository
     * is called and called with the correct object.
     */
    @Test
    public void testStoreAccessRequest() throws Exception {
        List<User> users = new ArrayList<>();
        users.add(user);
        List<AWSRdsDatabase> instances = new ArrayList<>();
        instances.add(awsRdsDatabase);

        Mockito.when(databaseConnectionService.checkUsersAndDbs(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(new HashMap<>());
        AccessRequestCreationResponse result = accessRequestService.storeAccessRequest(ownerRequestWrapper);

        Assert.assertTrue(result.getResponse() instanceof AccessRequest);
        AccessRequest response = (AccessRequest)result.getResponse();
        Assert.assertEquals(response.getRequestorEmail(), "testEmail@finra.org");
        Assert.assertEquals(response.getRequestorId(), "testUserId");
        Assert.assertEquals(response.getRequestorName(), "testName");
        Assert.assertEquals(response.getRegion(), "testRegion");
        Assert.assertEquals(response.getAccount(), "TESTACCOUNT");
        Assert.assertEquals(response.getAccountSdlc(), "dev");
        Assert.assertEquals(response.getDays(), new Integer(1));


        Assert.assertEquals(response.getUsers(), users);
        Assert.assertEquals(response.getAwsRdsInstances(), instances);

        verify(accessRequestRepository, times(1)).save(response);
    }

    /**
     * Test for making sure the storeAccessRequest method throws an exception if a prod request for datafix . Makes sure the accessRequestRepository
     * is called and called with the correct object.
     */
    @Test(expected=GatekeeperException.class)
    public void testStoreAccessRequestDaysBeyondMax() throws GatekeeperException {

        List<User> users = new ArrayList<>();
        users.add(user);
        List<AWSRdsDatabase> instances = new ArrayList<>();
        instances.add(awsRdsDatabase);

        AccessRequestWrapper badReq = new AccessRequestWrapper();
        badReq.setAccountSdlc("prod");
        badReq.setDays(181);
        badReq.setRoles(Arrays.asList(new UserRole("dba")));
        AccessRequestCreationResponse result = accessRequestService.storeAccessRequest(badReq);

        verify(accessRequestRepository, times(0)).save((AccessRequest)result.getResponse());
    }

    /**
     * Test for checking that, when the user is APPROVER, they should be able to see
     * any active request. Even ones that they do not own.
     */
    @Test
    public void testGetActiveRequestsAdmin() {
        when(gatekeeperRoleService.getRole()).thenReturn(GatekeeperRdsRole.APPROVER);
        List<ActiveAccessRequestWrapper> activeRequests = accessRequestService.getActiveRequests();
        Assert.assertTrue(activeRequests.size() == 2);

        ActiveAccessRequestWrapper ownerRequest = activeRequests.get(0);
        Assert.assertEquals(ownerRequest.getUserCount(), new Integer(1));
        Assert.assertEquals(ownerRequest.getInstanceCount(), new Integer(1));
        Assert.assertEquals(ownerRequest.getCreated().toString(), new Date(4500000).toString());
        Assert.assertEquals(ownerRequest.getTaskId(), "taskOne");
        ActiveAccessRequestWrapper nonOwnerRequest = activeRequests.get(1);
        Assert.assertEquals(nonOwnerRequest.getUserCount(), new Integer(0));
        Assert.assertEquals(nonOwnerRequest.getInstanceCount(), new Integer(1));
        Assert.assertEquals(nonOwnerRequest.getCreated().toString(), testDate.toString());
        Assert.assertEquals(nonOwnerRequest.getTaskId(), "taskTwo");
    }

    /**
     * Test for checking that, when the user is DEV, they should be able to see
     * only the requests that are active and were requested by themselves
     */
    @Test
    public void testGetActiveRequests() {
        when(gatekeeperRoleService.getUserProfile().getUserId()).thenReturn("owner");
        when(gatekeeperRoleService.getRole()).thenReturn(GatekeeperRdsRole.DEV);
        List<ActiveAccessRequestWrapper> activeRequests = accessRequestService.getActiveRequests();
        Assert.assertEquals(activeRequests.size(),1);

        ActiveAccessRequestWrapper ownerRequest = activeRequests.get(0);
        Assert.assertEquals(ownerRequest.getUserCount(), new Integer(1));
        Assert.assertEquals(ownerRequest.getInstanceCount(), new Integer(1));
        Assert.assertEquals(ownerRequest.getCreated().toString(), new Date(4500000).toString());
        Assert.assertEquals(ownerRequest.getTaskId(), "taskOne");


        when(gatekeeperRoleService.getUserProfile().getUserId()).thenReturn("non-owner");
        when(gatekeeperRoleService.getRole()).thenReturn(GatekeeperRdsRole.DEV);
        activeRequests = accessRequestService.getActiveRequests();
        Assert.assertEquals(activeRequests.size(),1);

        ActiveAccessRequestWrapper nonOwnerRequest = activeRequests.get(0);
        Assert.assertEquals(nonOwnerRequest.getUserCount(), new Integer(0));
        Assert.assertEquals(nonOwnerRequest.getInstanceCount(), new Integer(1));
        Assert.assertEquals(nonOwnerRequest.getCreated().toString(), testDate.toString());
        Assert.assertEquals(nonOwnerRequest.getTaskId(), "taskTwo");
    }

    /**
     * Test for checking that, when the user is APPROVER, they should be able to see
     * any completed request. Even ones that they do not own.
     */
    @Test
    public void testGetCompletedRequestsAdmin() {
        when(gatekeeperRoleService.getRole()).thenReturn(GatekeeperRdsRole.APPROVER);
        List<CompletedAccessRequestWrapper> completedRequests = accessRequestService.getCompletedRequests();
        Assert.assertTrue(completedRequests.size() == 2);


        CompletedAccessRequestWrapper nonOwnerRequest = completedRequests.get(0);
        Assert.assertEquals(nonOwnerRequest.getUserCount(), new Integer(0));
        Assert.assertEquals(nonOwnerRequest.getInstanceCount(), new Integer(1));
        Assert.assertEquals(nonOwnerRequest.getCreated().toString(), new Date(45002).toString());
        Assert.assertEquals(nonOwnerRequest.getUpdated().toString(), new Date(45003).toString());

        CompletedAccessRequestWrapper ownerRequest = completedRequests.get(1);
        Assert.assertEquals(ownerRequest.getUserCount(), new Integer(1));
        Assert.assertEquals(ownerRequest.getInstanceCount(), new Integer(1));
        Assert.assertEquals(ownerRequest.getCreated().toString(), new Date(45000).toString());
        Assert.assertEquals(ownerRequest.getUpdated().toString(), new Date(45002).toString());



    }

    /**
     * Test for checking that, when the user is DEV, they should be able to see
     * only the requests that are active and were requested by themselves
     */
    @Test
    public void testGetCompletedRequests() {
        when(gatekeeperRoleService.getUserProfile().getUserId()).thenReturn("owner");
        when(gatekeeperRoleService.getRole()).thenReturn(GatekeeperRdsRole.DEV);

        List<CompletedAccessRequestWrapper> completedRequests = accessRequestService.getCompletedRequests();
        Assert.assertTrue(completedRequests.size() == 1);

        CompletedAccessRequestWrapper ownerRequest = completedRequests.get(0);
        Assert.assertEquals(ownerRequest.getUserCount(), new Integer(1));
        Assert.assertEquals(ownerRequest.getInstanceCount(), new Integer(1));
        Assert.assertEquals(ownerRequest.getAttempts(), new Integer(1));
        Assert.assertEquals(ownerRequest.getCreated().toString(), new Date(45000).toString());
        Assert.assertEquals(ownerRequest.getUpdated().toString(), new Date(45002).toString());


        when(gatekeeperRoleService.getUserProfile().getUserId()).thenReturn("non-owner");
        when(gatekeeperRoleService.getRole()).thenReturn(GatekeeperRdsRole.DEV);

        completedRequests = accessRequestService.getCompletedRequests();
        Assert.assertEquals(completedRequests.size(),1);

        CompletedAccessRequestWrapper nonOwnerRequest = completedRequests.get(0);
        Assert.assertEquals(nonOwnerRequest.getUserCount(), new Integer(0));
        Assert.assertEquals(nonOwnerRequest.getInstanceCount(), new Integer(1));
        Assert.assertEquals(nonOwnerRequest.getAttempts(), new Integer(2));
        Assert.assertEquals(nonOwnerRequest.getCreated().toString(), new Date(45002).toString());
        Assert.assertEquals(nonOwnerRequest.getUpdated().toString(), new Date(45003).toString());

    }

    /**
     * Tests that the status and taskID are passed to the taskService correctly
     * when the request is approved.
     */
    @Test
    public void testApproval(){
        Mockito.when(accessRequestRepository.findOne(1L)).thenReturn(ownerRequest);
        accessRequestService.approveRequest("taskOne", 1L, "A reason");
        Map<String,Object> statusMap = new HashMap<>();
        statusMap.put("requestStatus", RequestStatus.APPROVAL_GRANTED);
        verify(accessRequestRepository, times(1)).save(Mockito.any(AccessRequest.class));
        verify(taskService,times(1)).setAssignee("taskOne","testUserId");
        verify(taskService,times(1)).complete("taskOne",statusMap);
    }

    /**
     * Tests that the status and taskID are passed to the taskService correctly
     * when the request is rejected.
     */
    @Test
    public void testRejected(){
        Mockito.when(accessRequestRepository.findOne(1L)).thenReturn(ownerRequest);
        accessRequestService.rejectRequest("taskOne", 1L, "Another Reason");
        Map<String,Object> statusMap = new HashMap<>();
        statusMap.put("requestStatus", RequestStatus.APPROVAL_REJECTED);
        verify(accessRequestRepository, times(1)).save(Mockito.any(AccessRequest.class));
        verify(taskService,times(1)).setAssignee("taskOne","testUserId");
        verify(taskService,times(1)).complete("taskOne",statusMap);
    }


    /**
     * Testing boundaries 
     */
    @Test
    public void testRoleBasedThresholds() throws Exception{
        List<UserRole> roles = new ArrayList<>();
        UserRole userRole = new UserRole();
        userRole.setRole("readonly");
        roles.add(userRole);
        when(ownerRequest.getRoles()).thenReturn(roles);
        
        Map<String, Map<String, Integer>> mockDev = new HashMap<>();
        Map<String, Integer> mockReadOnly = new HashMap<>();
        mockReadOnly.put("dev",1);
        mockReadOnly.put("qa",2);
        mockReadOnly.put("prod",3);
        Map<String, Integer> mockDba = new HashMap<>();
        mockDba.put("dev",4);
        mockDba.put("qa",5);
        mockDba.put("prod",6);
        Map<String, Integer> mockDatafix = new HashMap<>();
        mockDatafix.put("dev",7);
        mockDatafix.put("qa",8);
        mockDatafix.put("prod",9);
        mockDev.put("readonly", mockReadOnly);
        mockDev.put("datafix", mockDba);
        mockDev.put("dba", mockDatafix);
        when(approvalThreshold.getApprovalPolicy(GatekeeperRdsRole.DEV)).thenReturn(mockDev);
        when(approvalThreshold.getApprovalPolicy(GatekeeperRdsRole.OPS)).thenReturn(mockDev);
        when(approvalThreshold.getApprovalPolicy(GatekeeperRdsRole.DBA)).thenReturn(mockDev);

        when(ownerRequest.getDays()).thenReturn(2);
        Assert.assertTrue(accessRequestService.isApprovalNeeded(ownerRequest));
        when(ownerRequest.getAccountSdlc()).thenReturn("qa");
        when(ownerRequest.getDays()).thenReturn(3);
        Assert.assertTrue(accessRequestService.isApprovalNeeded(ownerRequest));
        when(ownerRequest.getAccountSdlc()).thenReturn("prod");
        when(ownerRequest.getDays()).thenReturn(4);
        Assert.assertTrue(accessRequestService.isApprovalNeeded(ownerRequest));
        
        roles.clear();
        userRole.setRole("datafix");
        roles.add(userRole);
        when(ownerRequest.getRoles()).thenReturn(roles);
        when(gatekeeperRoleService.getRole()).thenReturn(GatekeeperRdsRole.OPS);
        when(ownerRequest.getDays()).thenReturn(5);
        when(ownerRequest.getAccountSdlc()).thenReturn("dev");
        Assert.assertTrue(accessRequestService.isApprovalNeeded(ownerRequest));
        when(ownerRequest.getAccountSdlc()).thenReturn("qa");
        when(ownerRequest.getDays()).thenReturn(6);
        Assert.assertTrue(accessRequestService.isApprovalNeeded(ownerRequest));
        when(ownerRequest.getAccountSdlc()).thenReturn("prod");
        when(ownerRequest.getDays()).thenReturn(7);
        Assert.assertTrue(accessRequestService.isApprovalNeeded(ownerRequest));
        when(ownerRequest.getDays()).thenReturn(6);
        Assert.assertFalse(accessRequestService.isApprovalNeeded(ownerRequest));


        roles.clear();
        userRole.setRole("dba");
        roles.add(userRole);
        when(ownerRequest.getRoles()).thenReturn(roles);
        when(gatekeeperRoleService.getRole()).thenReturn(GatekeeperRdsRole.DBA);
        when(ownerRequest.getDays()).thenReturn(8);
        when(ownerRequest.getAccountSdlc()).thenReturn("dev");
        Assert.assertTrue(accessRequestService.isApprovalNeeded(ownerRequest));
        when(ownerRequest.getAccountSdlc()).thenReturn("qa");
        when(ownerRequest.getDays()).thenReturn(9);
        Assert.assertTrue(accessRequestService.isApprovalNeeded(ownerRequest));
        when(ownerRequest.getAccountSdlc()).thenReturn("prod");
        when(ownerRequest.getDays()).thenReturn(10);
        Assert.assertTrue(accessRequestService.isApprovalNeeded(ownerRequest));

    }

}
