import blockchain.Fabric;
import blockchain.SampleOrg;
import org.hyperledger.fabric.sdk.Chain;

public class Main {
    public static void main(String[] args) throws Exception {
        Fabric fabric = new Fabric("foo", "github.com/example_cc", new String[]{"a", "500", "b", "200"});
        SampleOrg sampleOrg = fabric.getConfiguredOrganisationParameters();
        Chain chain = fabric.initBlockchain(sampleOrg);
        String update = fabric.invokeChaincode(chain, new String[]{"move", "a", "b", "100"});
        System.out.println("update:" + update);
        String query = fabric.invokeChaincode(chain, new String[]{"query", "a"});
        System.out.println("query:" + query);
        query = fabric.invokeChaincode(chain, new String[]{"query", "b"});
        System.out.println("query:" + query);
    }
}
