package index;

import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;

/**
 * B+ Tree implementation managing tree structure operations natively across arbitrary logic layers.
 * @param <K> The type of the keys, which must be Comparable.
 * @param <V> The type of the values.
 */
public class BPlusTree<K extends Comparable<K>, V> {
    private Node<K, V> root;
    private int order;

    public BPlusTree(int order) {
        this.order = order;
        this.root = new LeafNode<>(order - 1);
    }

    public BPlusTree() {
        this(4); 
    }

    public void insert(K key, V value) {
        LeafNode<K, V> leaf = findLeaf(key);
        insertIntoLeaf(leaf, key, value);
    }

    private LeafNode<K, V> findLeaf(K key) {
        Node<K, V> curr = root;
        while (!curr.isLeaf()) {
            InternalNode<K, V> internal = (InternalNode<K, V>) curr;
            int index = Collections.binarySearch(internal.keys, key);
            if (index < 0) {
                index = -(index + 1);
            } else {
                index = index + 1;
            }
            curr = internal.getChild(index);
        }
        return (LeafNode<K, V>) curr;
    }

    private void insertIntoLeaf(LeafNode<K, V> leaf, K key, V value) {
        int index = Collections.binarySearch(leaf.keys, key);
        if (index >= 0) return; // duplicate

        int insertPos = -(index + 1);
        leaf.keys.add(insertPos, key);
        leaf.values.add(insertPos, value);

        if (leaf.keyCount() > leaf.maxKeys) {
            handleOverflow(leaf);
        }
    }

    private void handleOverflow(Node<K, V> node) {
        if (node.isLeaf()) {
            splitLeaf((LeafNode<K, V>) node);
        } else {
            splitInternal((InternalNode<K, V>) node);
        }
    }

    private void splitLeaf(LeafNode<K, V> leaf) {
        LeafNode<K, V> rightSibling = leaf.split();
        K promotedKey = rightSibling.keys.get(0);
        
        InternalNode<K, V> parent = leaf.getParent();
        if (parent == null) {
            createNewRoot(leaf, promotedKey, rightSibling);
        } else {
            int insertPos = Collections.binarySearch(parent.keys, promotedKey);
            if (insertPos < 0) insertPos = -(insertPos + 1);
            parent.insertKey(insertPos, promotedKey);
            parent.insertChild(insertPos + 1, rightSibling);
            
            if (parent.keyCount() > parent.maxKeys) {
                handleOverflow(parent);
            }
        }
    }

    private void splitInternal(InternalNode<K, V> internal) {
        K promotedKey = internal.keys.get(internal.keys.size() / 2);
        InternalNode<K, V> rightSibling = internal.split();
        
        InternalNode<K, V> parent = internal.getParent();
        if (parent == null) {
            createNewRoot(internal, promotedKey, rightSibling);
        } else {
            int insertPos = Collections.binarySearch(parent.keys, promotedKey);
            if (insertPos < 0) insertPos = -(insertPos + 1);
            parent.insertKey(insertPos, promotedKey);
            parent.insertChild(insertPos + 1, rightSibling);
            
            if (parent.keyCount() > parent.maxKeys) {
                handleOverflow(parent);
            }
        }
    }

    private void createNewRoot(Node<K, V> left, K promotedKey, Node<K, V> right) {
        InternalNode<K, V> newRoot = new InternalNode<>(order - 1);
        newRoot.insertKey(0, promotedKey);
        newRoot.insertChild(0, left);
        newRoot.insertChild(1, right);
        root = newRoot;
    }

    public V search(K key) {
        LeafNode<K, V> leaf = findLeaf(key);
        int index = Collections.binarySearch(leaf.keys, key);
        if (index >= 0) {
            return leaf.values.get(index);
        }
        return null;
    }

    public boolean contains(K key) {
        return search(key) != null;
    }

    public void delete(K key) {
        if (root == null || root.keyCount() == 0 && root.isLeaf()) {
            return;
        }

        LeafNode<K, V> leaf = findLeaf(key);
        int index = Collections.binarySearch(leaf.keys, key);
        
        if (index >= 0) {
            leaf.keys.remove(index);
            leaf.values.remove(index);
            
            if (leaf == root) {
                return; // Root leaf node can have any number of keys
            }
            
            int minKeys = (int) Math.ceil((order - 1) / 2.0);
            if (leaf.keyCount() < minKeys) {
                handleUnderflow(leaf);
            }
        }
    }

    private void handleUnderflow(Node<K, V> node) {
        if (node == root) {
            // Phase 4: Root Shrinkage
            if (!root.isLeaf() && root.keyCount() == 0) {
                root = ((InternalNode<K, V>) root).getChild(0);
                root.setParent(null);
            }
            return;
        }
        
        InternalNode<K, V> parent = node.getParent();
        int childIndex = parent.children.indexOf(node);
        
        if (node.isLeaf()) {
            LeafNode<K, V> leaf = (LeafNode<K, V>) node;
            LeafNode<K, V> leftSibling = childIndex > 0 ? (LeafNode<K, V>) parent.getChild(childIndex - 1) : null;
            LeafNode<K, V> rightSibling = childIndex < parent.childCount() - 1 ? (LeafNode<K, V>) parent.getChild(childIndex + 1) : null;
            
            int minKeys = (int) Math.ceil((order - 1) / 2.0);
            
            if (leftSibling != null && leftSibling.keyCount() > minKeys) {
                K borrowedKey = leftSibling.keys.remove(leftSibling.keys.size() - 1);
                V borrowedValue = leftSibling.values.remove(leftSibling.values.size() - 1);
                leaf.keys.add(0, borrowedKey);
                leaf.values.add(0, borrowedValue);
                parent.keys.set(childIndex - 1, leaf.keys.get(0));
            } else if (rightSibling != null && rightSibling.keyCount() > minKeys) {
                K borrowedKey = rightSibling.keys.remove(0);
                V borrowedValue = rightSibling.values.remove(0);
                leaf.keys.add(borrowedKey);
                leaf.values.add(borrowedValue);
                parent.keys.set(childIndex, rightSibling.keys.get(0));
            } else {
                if (leftSibling != null) {
                    leftSibling.keys.addAll(leaf.keys);
                    leftSibling.values.addAll(leaf.values);
                    leftSibling.next = leaf.next;
                    
                    parent.removeKey(childIndex - 1);
                    parent.removeChild(childIndex);
                } else if (rightSibling != null) {
                    leaf.keys.addAll(rightSibling.keys);
                    leaf.values.addAll(rightSibling.values);
                    leaf.next = rightSibling.next;
                    
                    parent.removeKey(childIndex);
                    parent.removeChild(childIndex + 1);
                }
                
                int parentMinKeys = (int) Math.ceil(order / 2.0) - 1;
                if (parent.keyCount() < parentMinKeys) {
                    handleUnderflow(parent);
                }
            }
        } else {
            // Phase 3: Internal Node Underflow Handling
            InternalNode<K, V> internal = (InternalNode<K, V>) node;
            InternalNode<K, V> leftSibling = childIndex > 0 ? (InternalNode<K, V>) parent.getChild(childIndex - 1) : null;
            InternalNode<K, V> rightSibling = childIndex < parent.childCount() - 1 ? (InternalNode<K, V>) parent.getChild(childIndex + 1) : null;
            
            int minKeys = (int) Math.ceil(order / 2.0) - 1;
            
            if (leftSibling != null && leftSibling.keyCount() > minKeys) {
                // Borrow from Left
                K borrowedKey = leftSibling.keys.remove(leftSibling.keys.size() - 1);
                Node<K, V> borrowedChild = leftSibling.children.remove(leftSibling.children.size() - 1);
                
                K parentKey = parent.keys.get(childIndex - 1);
                parent.keys.set(childIndex - 1, borrowedKey);
                
                internal.keys.add(0, parentKey);
                internal.children.add(0, borrowedChild);
                borrowedChild.setParent(internal);
                
            } else if (rightSibling != null && rightSibling.keyCount() > minKeys) {
                // Borrow from Right
                K borrowedKey = rightSibling.keys.remove(0);
                Node<K, V> borrowedChild = rightSibling.children.remove(0);
                
                K parentKey = parent.keys.get(childIndex);
                parent.keys.set(childIndex, borrowedKey);
                
                internal.keys.add(parentKey);
                internal.children.add(borrowedChild);
                borrowedChild.setParent(internal);
                
            } else {
                // Merge Sub-trees
                if (leftSibling != null) {
                    K parentKey = parent.keys.get(childIndex - 1);
                    leftSibling.keys.add(parentKey);
                    leftSibling.keys.addAll(internal.keys);
                    
                    for (Node<K, V> child : internal.children) {
                        leftSibling.children.add(child);
                        child.setParent(leftSibling);
                    }
                    
                    parent.removeKey(childIndex - 1);
                    parent.removeChild(childIndex);
                } else if (rightSibling != null) {
                    K parentKey = parent.keys.get(childIndex);
                    internal.keys.add(parentKey);
                    internal.keys.addAll(rightSibling.keys);
                    
                    for (Node<K, V> child : rightSibling.children) {
                        internal.children.add(child);
                        child.setParent(internal);
                    }
                    
                    parent.removeKey(childIndex);
                    parent.removeChild(childIndex + 1);
                }
                
                int parentMinKeys = (int) Math.ceil(order / 2.0) - 1;
                if (parent.keyCount() < parentMinKeys) {
                    handleUnderflow(parent);
                }
            }
        }
    }

    public int size() {
        int count = 0;
        Node<K, V> curr = root;
        while (!curr.isLeaf()) {
            curr = ((InternalNode<K, V>) curr).getChild(0);
        }
        LeafNode<K, V> leaf = (LeafNode<K, V>) curr;
        while (leaf != null) {
            count += leaf.keyCount();
            leaf = leaf.next;
        }
        return count;
    }

    public int height() {
        int h = 1;
        Node<K, V> curr = root;
        while (!curr.isLeaf()) {
            curr = ((InternalNode<K, V>) curr).getChild(0);
            h++;
        }
        return h;
    }

    public void printTree() {
        Queue<Node<K, V>> queue = new LinkedList<>();
        queue.add(root);
        int level = 0;

        while (!queue.isEmpty()) {
            int size = queue.size();
            System.out.print("Level " + level + ": ");
            for (int i = 0; i < size; i++) {
                Node<K, V> curr = queue.poll();
                System.out.print(curr.keys + " ");
                if (!curr.isLeaf()) {
                    InternalNode<K, V> internal = (InternalNode<K, V>) curr;
                    for (int j = 0; j < internal.childCount(); j++) {
                        queue.add(internal.getChild(j));
                    }
                }
            }
            System.out.println();
            level++;
        }
    }
}
