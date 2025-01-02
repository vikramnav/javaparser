import java.util.HashMap;
import java.util.Map;

class LRUCache<K, V> {
    private final int capacity;
    private final Map<K, Node> cache;
    private final DoublyLinkedList<K, V> list;

    // Node for the doubly linked list
    private class Node {
        K key;
        V value;
        Node prev, next;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    // Doubly linked list to store the cache in access order
    private class DoublyLinkedList<K, V> {
        private Node head, tail;

        DoublyLinkedList() {
            head = new Node(null, null);
            tail = new Node(null, null);
            head.next = tail;
            tail.prev = head;
        }

        void addFirst(Node node) {
            node.next = head.next;
            node.prev = head;
            head.next.prev = node;
            head.next = node;
        }

        void remove(Node node) {
            node.prev.next = node.next;
            node.next.prev = node.prev;
        }

        Node removeLast() {
            if (head.next == tail) {
                return null; // Empty list
            }
            Node last = tail.prev;
            remove(last);
            return last;
        }
    }

    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.cache = new HashMap<>();
        this.list = new DoublyLinkedList<>();
    }

    public synchronized V get(K key) {
        if (!cache.containsKey(key)) {
            return null; // If the key is not in the cache
        }
        Node node = cache.get(key);
        list.remove(node);
        list.addFirst(node); // Move the node to the front (most recently used)
        return node.value;
    }

    public synchronized void put(K key, V value) {
        if (cache.containsKey(key)) {
            Node node = cache.get(key);
            node.value = value; // Update the value
            list.remove(node);
            list.addFirst(node); // Move to the front
        } else {
            if (cache.size() == capacity) {
                Node last = list.removeLast();
                cache.remove(last.key); // Remove the least recently used item
            }
            Node newNode = new Node(key, value);
            cache.put(key, newNode);
            list.addFirst(newNode); // Add to the front
        }
    }
}
