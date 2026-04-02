import demo.ConsoleDemo;
import tests.BlockCRDTTest;
import tests.CharCRDTTest;
import tests.IntegrationTest;

public class Main {
    public static void main(String[] args) {
        new CharCRDTTest().runAll();
        new BlockCRDTTest().runAll();
        new IntegrationTest().runAll();
        ConsoleDemo.main(null);
    }
}