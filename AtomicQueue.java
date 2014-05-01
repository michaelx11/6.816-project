import org.deuce.Atomic;


class AtomicQueue<T> {
  private Node<T> head;
  private Node<T> tail;
  @SuppressWarnings({"unchecked"})
  public AtomicQueue() {
    head = new Node<T>(null);
    tail = head;
  }
  @Atomic
  public void enq(T x) {
    Node<T> node = new Node<T>(x);
    tail.next = node;
    tail = node;
  }
  public T deq() throws EmptyException {
    if (head.next == null)
      throw new EmptyException();
    else {
      Node<T> x = head.next;
      head.next = x.next;
      return x.item;
    }
  }
}

class Node<T> {
  T item;
  Node<T> next;
  public Node(T item) {
    this.item = item;
    this.next = null;
  }
}

class EmptyException extends Exception {
  private static final long serialVersionUID = 1L;
  public EmptyException() {
    super();
  } 
}
