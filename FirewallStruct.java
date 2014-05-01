import java.util.*;
import java.io.*;

class FirewallStruct {
  public Set<Integer> sourceSet;
  public HashMap<Integer, Set<Integer>> destMap;
  public int[] histogram;

  public FirewallStruct() {
    sourceSet = new HashSet<Integer>();
    destMap = new HashMap<Integer, Set<Integer>>();
    histogram = new int[1 << 16];
  }
}
