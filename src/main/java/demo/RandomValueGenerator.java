package demo;

import java.util.Random;

public class RandomValueGenerator {
    private static final Random random = new Random();
    
    public static int generateValue() {
        return random.nextInt(1000);
    }
}
