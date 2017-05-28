package blockchain;

import org.apache.commons.codec.binary.Hex;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.TransactionEventException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

public class Fabric {
    private static final SampleConfig SAMPLE_CONFIG = SampleConfig.getConfig();
    private static final String TEST_ADMIN_NAME = "admin";
    private static final String TESTUSER_1_NAME = "user1";
    private static final String TEST_FIXTURES_PATH = "network";
    private static final String CHAIN_CODE_SRC_LOCATION = "chaincode";
    private static final String CHAIN_CODE_VERSION = "1";
    private static final String CHAIN_NAME = "luxoft";
    private final String CHAIN_CODE_NAME;
    private final String CHAIN_CODE_PATH;
    private final String CHAIN_CODE_INIT_ARG[];
    private final ChainCodeID chainCodeID;
    private final SampleConfigHelper configHelper = new SampleConfigHelper();
    private final HFClient client = HFClient.createNewInstance();
    String testTxID = null;  // save the CC invoke TxID and use in queries
    private Collection<SampleOrg> testSampleOrgs;

    public Fabric(String chainCodeName, String chaincodePath, String[] chaincodeInitArg) {
        CHAIN_CODE_NAME = chainCodeName;
        CHAIN_CODE_PATH = chaincodePath;
        chainCodeID = ChainCodeID.newBuilder().setName(CHAIN_CODE_NAME).setVersion(CHAIN_CODE_VERSION).setPath(CHAIN_CODE_PATH).build();
        CHAIN_CODE_INIT_ARG = chaincodeInitArg;
        try {
            client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            checkConfig();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void fail(String message) {
        throw new RuntimeException(message);
    }

    private static void assertTrue(Boolean data) {
        if (!data)
            throw new RuntimeException("should be true");
    }

    private static void assertEquals(Number object1, Number object2) {
        if (new BigDecimal(object1.doubleValue()).compareTo(new BigDecimal(object2.doubleValue())) != 0)
            throw new RuntimeException("should be equal");
    }

    private static void assertEquals(Object object1, Object object2) {
        if (!object1.equals(object2))
            throw new RuntimeException("should be equal");
    }

    static void out(String format, Object... args) {

        System.err.flush();
        System.out.flush();

        System.out.println(format(format, args));
        System.err.flush();
        System.out.flush();

    }

    private void checkConfig() throws Exception {
        configHelper.clearConfig();
        configHelper.customizeConfig();

        testSampleOrgs = SAMPLE_CONFIG.getIntegrationTestsSampleOrgs();
        //Set up hfca for each sample org

        for (SampleOrg sampleOrg : testSampleOrgs) {
            sampleOrg.setCAClient(HFCAClient.createNewInstance(sampleOrg.getCALocation(), sampleOrg.getCAProperties()));
        }
    }

    private void clearConfig() {
        try {
            configHelper.clearConfig();
        } catch (Exception e) {
        }
    }

    private void createUsers(SampleStore sampleStore) throws Exception {
        for (SampleOrg sampleOrg : testSampleOrgs) {

            HFCAClient ca = sampleOrg.getCAClient();
            final String orgName = sampleOrg.getName();
            final String mspid = sampleOrg.getMSPID();
            ca.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
            SampleUser admin = sampleStore.getMember(TEST_ADMIN_NAME, orgName);
            if (!admin.isEnrolled()) {  //Preregistered admin only needs to be enrolled with Fabric caClient.
                admin.setEnrollment(ca.enroll(admin.getName(), "adminpw"));
                admin.setMPSID(mspid);
            }

            sampleOrg.setAdmin(admin); // The admin of this org --

            SampleUser user = sampleStore.getMember(TESTUSER_1_NAME, sampleOrg.getName());
            if (!user.isRegistered()) {  // users need to be registered AND enrolled
                RegistrationRequest rr = new RegistrationRequest(user.getName(), "org1.department1");
                user.setEnrollmentSecret(ca.register(rr, admin));
            }
            if (!user.isEnrolled()) {
                user.setEnrollment(ca.enroll(user.getName(), user.getEnrollmentSecret()));
                user.setMPSID(mspid);
            }
            sampleOrg.addUser(user); //Remember user belongs to this Org

            final String sampleOrgName = sampleOrg.getName();
            final String sampleOrgDomainName = sampleOrg.getDomainName();

            SampleUser peerOrgAdmin = sampleStore.getMember(sampleOrgName + "Admin", sampleOrgName, sampleOrg.getMSPID(),
                    findFile_sk(Paths.get(SAMPLE_CONFIG.getTestChannlePath(), "crypto-config/peerOrganizations/",
                            sampleOrgDomainName, format("/users/Admin@%s/msp/keystore", sampleOrgDomainName)).toFile()),
                    Paths.get(SAMPLE_CONFIG.getTestChannlePath(), "crypto-config/peerOrganizations/", sampleOrgDomainName,
                            format("/users/Admin@%s/msp/signcerts/Admin@%s-cert.pem", sampleOrgDomainName, sampleOrgDomainName)).toFile());

            sampleOrg.setPeerAdmin(peerOrgAdmin); //A special user that can crate channels, join peers and install chain code
            // and jump tall blockchains in a single leap!
        }
    }

//    private void addPeers(Chain chain, SampleOrg sampleOrg) throws Exception{
//        for (String peerName : sampleOrg.getPeerNames()) {
//            String peerLocation = sampleOrg.getPeerLocation(peerName);
//
//            Properties peerProperties = SAMPLE_CONFIG.getPeerProperties(peerName);//test properties for peer.. if any.
//            if (peerProperties == null) {
//                peerProperties = new Properties();
//            }
//            //Example of setting specific options on grpc's ManagedChannelBuilder
//            peerProperties.put("grpc.ManagedChannelBuilderOption.maxInboundMessageSize", 9000000);
//
//            Peer peer = client.newPeer(peerName, peerLocation, peerProperties);
//            chain.addPeer(peer);
//            out("Peer %s added to chain %s", peerName, chain.getName());
//            sampleOrg.addPeer(peer);
//        }
//    }
//
//    private void addOrderers(Chain chain, SampleOrg sampleOrg) throws Exception{
//        for (String orderName : sampleOrg.getOrdererNames()) {
//            chain.addOrderer(client.newOrderer(orderName, sampleOrg.getOrdererLocation(orderName),
//                    SAMPLE_CONFIG.getOrdererProperties(orderName)));
//        }
//    }

    private CompletableFuture<BlockEvent.TransactionEvent> installChaincode(Chain chain, SampleOrg sampleOrg) throws Exception {
        final String chainName = chain.getName();
        out("Running Chain %s", chainName);
        chain.setTransactionWaitTime(SAMPLE_CONFIG.getTransactionWaitTime());
        chain.setDeployWaitTime(SAMPLE_CONFIG.getDeployWaitTime());

        Collection<Peer> channelPeers = chain.getPeers();
        Collection<Orderer> orderers = chain.getOrderers();
        Collection<ProposalResponse> responses;
        Collection<ProposalResponse> successful = new LinkedList<>();
        Collection<ProposalResponse> failed = new LinkedList<>();

        out("Creating install proposal");

        InstallProposalRequest installProposalRequest = client.newInstallProposalRequest();
        installProposalRequest.setChaincodeID(chainCodeID);
        ////For GO language and serving just a single user, chaincodeSource is mostly likely the users GOPATH
        installProposalRequest.setChaincodeSourceLocation(new File(CHAIN_CODE_SRC_LOCATION));
        installProposalRequest.setChaincodeVersion(CHAIN_CODE_VERSION);

        out("Sending install proposal");

        ////////////////////////////
        // only a client from the same org as the peer can issue an install request
        int numInstallProposal = 0;
        //    Set<String> orgs = orgPeers.keySet();
        //   for (SampleOrg org : testSampleOrgs) {

        Set<Peer> peersFromOrg = sampleOrg.getPeers();
        numInstallProposal = numInstallProposal + peersFromOrg.size();
        responses = client.sendInstallProposal(installProposalRequest, peersFromOrg);

        for (ProposalResponse response : responses) {
            if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                out("Successful install proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
                successful.add(response);
            } else {
                failed.add(response);
            }
        }
        //   }
        out("Received %d install proposal responses. Successful+verified: %d . Failed: %d", numInstallProposal, successful.size(), failed.size());

        if (failed.size() > 0) {
            ProposalResponse first = failed.iterator().next();
            fail("Not enough endorsers for install :" + successful.size() + ".  " + first.getMessage());
        }

        //   client.setUserContext(sampleOrg.getUser(TEST_ADMIN_NAME));
        //  final ChainCodeID chainCodeID = firstInstallProposalResponse.getChainCodeID();
        // Note install chain code does not require transaction no need to
        // send to Orderers

        ///////////////
        //// Instantiate chain code.
        InstantiateProposalRequest instantiateProposalRequest = client.newInstantiationProposalRequest();
        instantiateProposalRequest.setProposalWaitTime(60000);
        instantiateProposalRequest.setChaincodeID(chainCodeID);
        instantiateProposalRequest.setFcn("init");
        instantiateProposalRequest.setArgs(CHAIN_CODE_INIT_ARG);
        Map<String, byte[]> tm = new HashMap<>();
        tm.put("HyperLedgerFabric", "InstantiateProposalRequest:JavaSDK".getBytes(UTF_8));
        tm.put("method", "InstantiateProposalRequest".getBytes(UTF_8));
        instantiateProposalRequest.setTransientMap(tm);

            /*
              policy OR(Org1MSP.member, Org2MSP.member) meaning 1 signature from someone in either Org1 or Org2
              See README.md Chaincode endorsement policies section for more details.
            */
        ChaincodeEndorsementPolicy chaincodeEndorsementPolicy = new ChaincodeEndorsementPolicy();
        chaincodeEndorsementPolicy.fromYamlFile(new File(TEST_FIXTURES_PATH + "/chaincodeendorsementpolicy.yaml"));
        instantiateProposalRequest.setChaincodeEndorsementPolicy(chaincodeEndorsementPolicy);

        out("Sending instantiateProposalRequest to all peers");
        successful.clear();
        failed.clear();

        //         client.setUserContext(sampleOrg.getAdmin());

        responses = chain.sendInstantiationProposal(instantiateProposalRequest, chain.getPeers());
        for (ProposalResponse response : responses) {
            if (response.isVerified() && response.getStatus() == ProposalResponse.Status.SUCCESS) {
                successful.add(response);
                out("Succesful instantiate proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
            } else {
                failed.add(response);
            }
        }
        out("Received %d instantiate proposal responses. Successful+verified: %d . Failed: %d", responses.size(), successful.size(), failed.size());
        if (failed.size() > 0) {
            ProposalResponse first = failed.iterator().next();
            fail("Not enough endorsers for instantiate :" + successful.size() + "endorser failed with " + first.getMessage() + ". Was verified:" + first.isVerified());
        }

        ///////////////
        /// Send instantiate transaction to orderer
        out("Sending instantiateTransaction to orderer");
        CompletableFuture<BlockEvent.TransactionEvent> result = chain.sendTransaction(successful, orderers);
        return result;
    }

    public Chain initBlockchain(SampleOrg sampleOrg) throws Exception {
        File sampleStoreFile = new File("samplestore.properties");
//        if (sampleStoreFile.exists()) { //For testing start fresh
//            sampleStoreFile.delete();
//        }
        final SampleStore sampleStore = new SampleStore(sampleStoreFile);

        String usersEnrolled = sampleStore.getValue("usersEnrolled");
        if (usersEnrolled == null) {
            createUsers(sampleStore);
            sampleStore.setValue("usersEnrolled", "true");
        } else {
            SampleUser adminUser = sampleStore.getMember(sampleOrg.getName() + "Admin", sampleOrg.getName());
            sampleOrg.setPeerAdmin(adminUser);
        }
        client.setUserContext(sampleOrg.getPeerAdmin());

        String chainStarted = sampleStore.getValue("isInited_" + CHAIN_NAME);
        Chain chain = constructChain(CHAIN_NAME, sampleOrg, chainStarted == null);
        if (chainStarted == null)
            sampleStore.setValue("isInited_" + CHAIN_NAME, "true");

        String chaincodeIsInited = sampleStore.getValue("isInited_" + CHAIN_CODE_NAME + "_" + CHAIN_CODE_VERSION);
        if (chaincodeIsInited == null) {
            installChaincode(chain, sampleOrg).get();
            sampleStore.setValue("isInited_" + CHAIN_CODE_NAME + "_" + CHAIN_CODE_VERSION, "true");
        }

        return chain;
    }

    public SampleOrg getConfiguredOrganisationParameters() {
        return SAMPLE_CONFIG.getIntegrationTestsSampleOrg("peerOrg1");
    }

    private void run() {

        try {

            SampleOrg sampleOrg = SAMPLE_CONFIG.getIntegrationTestsSampleOrg("peerOrg1");

            Chain chain = initBlockchain(sampleOrg);

            runChain(chain, sampleOrg);
            chain.shutdown(true); // Force foo chain to shutdown clean up resources.

            out("That's all folks!");

        } catch (Exception e) {
            e.printStackTrace();

            fail(e.getMessage());
        }

    }

    public String invokeChaincode(Chain chain, String[] arguments) throws Exception {
        Collection<ProposalResponse> successful = new LinkedList<>();
        Collection<ProposalResponse> failed = new LinkedList<>();

        // client.setUserContext(sampleOrg.getUser(TESTUSER_1_NAME));
        //       client.setUserContext(sampleOrg.getAdmin());

        ///////////////
        /// Send transaction proposal to all peers
        TransactionProposalRequest transactionProposalRequest = client.newTransactionProposalRequest();
        transactionProposalRequest.setChaincodeID(chainCodeID);
        transactionProposalRequest.setFcn("invoke");
        transactionProposalRequest.setArgs(arguments);

        Map<String, byte[]> tm2 = new HashMap<>();
        tm2.put("HyperLedgerFabric", "TransactionProposalRequest:JavaSDK".getBytes(UTF_8));
        tm2.put("method", "TransactionProposalRequest".getBytes(UTF_8));
        tm2.put("result", ":)".getBytes(UTF_8));  /// This should be returned see chaincode.
        transactionProposalRequest.setTransientMap(tm2);

        Collection<ProposalResponse> transactionPropResp = chain.sendTransactionProposal(transactionProposalRequest, chain.getPeers());
        for (ProposalResponse response : transactionPropResp) {
            if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                out("Successful transaction proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
                successful.add(response);
            } else {
                failed.add(response);
            }
        }
        out("Received %d transaction proposal responses. Successful+verified: %d . Failed: %d",
                transactionPropResp.size(), successful.size(), failed.size());
        if (failed.size() > 0) {
            ProposalResponse firstTransactionProposalResponse = failed.iterator().next();
            fail("Not enough endorsers for invoke:" + failed.size() + " endorser error: " +
                    firstTransactionProposalResponse.getMessage() +
                    ". Was verified: " + firstTransactionProposalResponse.isVerified());
        }
        out("Successfully received transaction proposal responses.");

        ProposalResponse resp = transactionPropResp.iterator().next();
        byte[] x = resp.getChainCodeActionResponsePayload();
        String resultAsString = null;
        if (x != null) {
            resultAsString = new String(x, "UTF-8");
        }

        BlockEvent.TransactionEvent transactionEvent = chain.sendTransaction(successful).get(SAMPLE_CONFIG.getTransactionWaitTime(), TimeUnit.SECONDS);
        return resultAsString;
    }

    private void runChain(Chain chain, SampleOrg sampleOrg) {

        try {

            CompletableFuture.<BlockEvent.TransactionEvent>supplyAsync(() -> {

                try {
                    Collection<ProposalResponse> successful = new LinkedList<>();
                    Collection<ProposalResponse> failed = new LinkedList<>();

                    // client.setUserContext(sampleOrg.getUser(TESTUSER_1_NAME));
                    //       client.setUserContext(sampleOrg.getAdmin());

                    ///////////////
                    /// Send transaction proposal to all peers
                    TransactionProposalRequest transactionProposalRequest = client.newTransactionProposalRequest();
                    transactionProposalRequest.setChaincodeID(chainCodeID);
                    transactionProposalRequest.setFcn("invoke");
                    transactionProposalRequest.setArgs(new String[]{"move", "a", "b", "100"});

                    Map<String, byte[]> tm2 = new HashMap<>();
                    tm2.put("HyperLedgerFabric", "TransactionProposalRequest:JavaSDK".getBytes(UTF_8));
                    tm2.put("method", "TransactionProposalRequest".getBytes(UTF_8));
                    tm2.put("result", ":)".getBytes(UTF_8));  /// This should be returned see chaincode.
                    transactionProposalRequest.setTransientMap(tm2);

                    out("sending transactionProposal to all peers with arguments: move(a,b,100)");

                    Collection<ProposalResponse> transactionPropResp = chain.sendTransactionProposal(transactionProposalRequest, chain.getPeers());
                    for (ProposalResponse response : transactionPropResp) {
                        if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                            out("Successful transaction proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
                            successful.add(response);
                        } else {
                            failed.add(response);
                        }
                    }
                    out("Received %d transaction proposal responses. Successful+verified: %d . Failed: %d",
                            transactionPropResp.size(), successful.size(), failed.size());
                    if (failed.size() > 0) {
                        ProposalResponse firstTransactionProposalResponse = failed.iterator().next();
                        fail("Not enough endorsers for invoke(move a,b,100):" + failed.size() + " endorser error: " +
                                firstTransactionProposalResponse.getMessage() +
                                ". Was verified: " + firstTransactionProposalResponse.isVerified());
                    }
                    out("Successfully received transaction proposal responses.");

                    ProposalResponse resp = transactionPropResp.iterator().next();
                    byte[] x = resp.getChainCodeActionResponsePayload();
                    String resultAsString = null;
                    if (x != null) {
                        resultAsString = new String(x, "UTF-8");
                    }
                    assertEquals(":)", resultAsString);
                    ////////////////////////////
                    // Send Transaction Transaction to orderer
                    out("Sending chain code transaction(move a,b,100) to orderer.");
                    return chain.sendTransaction(successful).get(SAMPLE_CONFIG.getTransactionWaitTime(), TimeUnit.SECONDS);

                } catch (Exception e) {
                    out("Caught an exception while invoking chaincode");
                    e.printStackTrace();
                    fail("Failed invoking chaincode with error : " + e.getMessage());
                }

                return null;

            }).thenApply(transactionEvent -> {
                try {

                    assertTrue(transactionEvent.isValid()); // must be valid to be here.
                    out("Finished transaction with transaction id %s", transactionEvent.getTransactionID());
                    testTxID = transactionEvent.getTransactionID(); // used in the channel queries later

                    ////////////////////////////
                    // Send Query Proposal to all peers
                    //
                    String expect = "300";
                    out("Now query chain code for the value of b.");
                    QueryByChaincodeRequest queryByChaincodeRequest = client.newQueryProposalRequest();
                    queryByChaincodeRequest.setArgs(new String[]{"query", "b"});
                    queryByChaincodeRequest.setFcn("invoke");
                    queryByChaincodeRequest.setChaincodeID(chainCodeID);

                    Map<String, byte[]> tm2 = new HashMap<>();
                    tm2.put("HyperLedgerFabric", "QueryByChaincodeRequest:JavaSDK".getBytes(UTF_8));
                    tm2.put("method", "QueryByChaincodeRequest".getBytes(UTF_8));
                    queryByChaincodeRequest.setTransientMap(tm2);

                    Collection<ProposalResponse> queryProposals = chain.queryByChaincode(queryByChaincodeRequest, chain.getPeers());
                    for (ProposalResponse proposalResponse : queryProposals) {
                        if (!proposalResponse.isVerified() || proposalResponse.getStatus() != ProposalResponse.Status.SUCCESS) {
                            fail("Failed query proposal from peer " + proposalResponse.getPeer().getName() + " status: " + proposalResponse.getStatus() +
                                    ". Messages: " + proposalResponse.getMessage()
                                    + ". Was verified : " + proposalResponse.isVerified());
                        } else {
                            String payload = proposalResponse.getProposalResponse().getResponse().getPayload().toStringUtf8();
                            out("Query payload of b from peer %s returned %s", proposalResponse.getPeer().getName(), payload);
                            assertEquals(payload, expect);
                        }
                    }

                    return null;
                } catch (Exception e) {
                    out("Caught exception while running query");
                    e.printStackTrace();
                    fail("Failed during chaincode query with error : " + e.getMessage());
                }

                return null;
            }).exceptionally(e -> {
                if (e instanceof TransactionEventException) {
                    BlockEvent.TransactionEvent te = ((TransactionEventException) e).getTransactionEvent();
                    if (te != null) {
                        fail(format("Transaction with txid %s failed. %s", te.getTransactionID(), e.getMessage()));
                    }
                }
                fail(format("Test failed with %s exception %s", e.getClass().getName(), e.getMessage()));

                return null;
            }).get(SAMPLE_CONFIG.getTransactionWaitTime(), TimeUnit.SECONDS);

            // Channel queries

            // We can only send channel queries to peers that are in the same org as the SDK user context
            // Get the peers from the current org being used and pick one randomly to send the queries to.
            Set<Peer> peerSet = sampleOrg.getPeers();
            Peer queryPeer = peerSet.iterator().next();
            out("Using peer %s for channel queries", queryPeer.getName());

            BlockchainInfo channelInfo = chain.queryBlockchainInfo(queryPeer);
            final String chainName = chain.getName();
            out("Channel info for : " + chainName);
            out("Channel height: " + channelInfo.getHeight());
            String chainCurrentHash = Hex.encodeHexString(channelInfo.getCurrentBlockHash());
            String chainPreviousHash = Hex.encodeHexString(channelInfo.getPreviousBlockHash());
            out("Channel current block hash: " + chainCurrentHash);
            out("Channel previous block hash: " + chainPreviousHash);

            // Query by block number. Should return latest block, i.e. block number 2
            BlockInfo returnedBlock = chain.queryBlockByNumber(queryPeer, channelInfo.getHeight() - 1);
            String previousHash = Hex.encodeHexString(returnedBlock.getPreviousHash());
            out("queryBlockByNumber returned correct block with blockNumber " + returnedBlock.getBlockNumber()
                    + " \n previous_hash " + previousHash);
            assertEquals(channelInfo.getHeight() - 1, returnedBlock.getBlockNumber());
            assertEquals(chainPreviousHash, previousHash);

            // Query by block hash. Using latest block's previous hash so should return block number 1
            byte[] hashQuery = returnedBlock.getPreviousHash();
            returnedBlock = chain.queryBlockByHash(queryPeer, hashQuery);
            out("queryBlockByHash returned block with blockNumber " + returnedBlock.getBlockNumber());
            assertEquals(channelInfo.getHeight() - 2, returnedBlock.getBlockNumber());

            // Query block by TxID. Since it's the last TxID, should be block 2
            returnedBlock = chain.queryBlockByTransactionID(queryPeer, testTxID);
            out("queryBlockByTxID returned block with blockNumber " + returnedBlock.getBlockNumber());
            assertEquals(channelInfo.getHeight() - 1, returnedBlock.getBlockNumber());

            // query transaction by ID
            TransactionInfo txInfo = chain.queryTransactionByID(queryPeer, testTxID);
            out("QueryTransactionByID returned TransactionInfo: txID " + txInfo.getTransactionID()
                    + "\n     validation code " + txInfo.getValidationCode().getNumber());

            out("Running for Chain %s done", chainName);

        } catch (Exception e) {
            out("Caught an exception running chain %s", chain.getName());
            e.printStackTrace();
            fail("Test failed with error : " + e.getMessage());
        }
    }

    private Chain constructChain(String name, SampleOrg sampleOrg, boolean newChain) throws Exception {
        //////////////////////////// TODo Needs to be made out of bounds and here chain just retrieved
        //Construct the chain
        //

        out("Constructing chain %s", name);

        Collection<Orderer> orderers = new LinkedList<>();

        for (String orderName : sampleOrg.getOrdererNames()) {
            orderers.add(client.newOrderer(orderName, sampleOrg.getOrdererLocation(orderName),
                    SAMPLE_CONFIG.getOrdererProperties(orderName)));
        }

        Chain chain;
        if (newChain) {
            //Just pick the first orderer in the list to create the chain.
            Orderer anOrderer = orderers.iterator().next();
            orderers.remove(anOrderer);
            ChainConfiguration chainConfiguration = new ChainConfiguration(new File(TEST_FIXTURES_PATH + "/e2e-2Orgs/channel/" + name + ".tx"));
            //Create chain that has only one signer that is this orgs peer admin. If chain creation policy needed more signature they would need to be added too.
            chain = client.newChain(name, anOrderer, chainConfiguration, client.getChainConfigurationSignature(chainConfiguration, sampleOrg.getPeerAdmin()));
        } else
            chain = client.newChain(name);

        out("Created chain %s", name);

        for (String peerName : sampleOrg.getPeerNames()) {
            String peerLocation = sampleOrg.getPeerLocation(peerName);

            Properties peerProperties = SAMPLE_CONFIG.getPeerProperties(peerName);//test properties for peer.. if any.
            if (peerProperties == null) {
                peerProperties = new Properties();
            }
            //Example of setting specific options on grpc's ManagedChannelBuilder
            peerProperties.put("grpc.ManagedChannelBuilderOption.maxInboundMessageSize", 9000000);

            Peer peer = client.newPeer(peerName, peerLocation, peerProperties);
            if (newChain) {
                chain.joinPeer(peer);
                out("Peer %s joined chain %s", peerName, name);
            } else {
                chain.addPeer(peer);
                out("Peer %s added to chain %s", peerName, name);
            }
            sampleOrg.addPeer(peer);
        }

        for (Orderer orderer : orderers) { //add remaining orderers if any.
            chain.addOrderer(orderer);
        }

        for (String eventHubName : sampleOrg.getEventHubNames()) {
            EventHub eventHub = client.newEventHub(eventHubName, sampleOrg.getEventHubLocation(eventHubName),
                    SAMPLE_CONFIG.getEventHubProperties(eventHubName));
            chain.addEventHub(eventHub);
        }

        chain.initialize();

        out("Finished initialization chain %s", name);

        return chain;

    }

    File findFile_sk(File directory) {

        File[] matches = directory.listFiles((dir, name) -> name.endsWith("_sk"));

        if (null == matches) {
            throw new RuntimeException(format("Matches returned null does %s directory exist?", directory.getAbsoluteFile().getName()));
        }

        if (matches.length != 1) {
            throw new RuntimeException(format("Expected in %s only 1 sk file but found %d", directory.getAbsoluteFile().getName(), matches.length));
        }

        return matches[0];

    }
}
