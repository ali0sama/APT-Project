import tests.BlockCRDTTest;
import tests.CharCRDTTest;
import tests.IntegrationTest;
import tests.SpecialCaseTest;

public class Main {
    public static void main(String[] args) {
        new CharCRDTTest().runAll();
        new BlockCRDTTest().runAll();
        new IntegrationTest().runAll();
        new SpecialCaseTest().runAll();
        //ConsoleDemo.main(null); could be used next phase for a simple console-based demo of the CRDT in action
    }
}