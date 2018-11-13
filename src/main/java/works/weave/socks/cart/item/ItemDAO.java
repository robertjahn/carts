package works.weave.socks.cart.item;

import works.weave.socks.cart.entities.Item;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public interface ItemDAO {
    Item save(Item item);

    void destroy(Item item);

    Optional<Item> findOne(String id);

    class Fake implements ItemDAO {
        private Map<String, Item> store = new HashMap<>();

        @Override
        public Item save(Item item) {
            return store.put(item.itemId(), item);
        }

        @Override
        public void destroy(Item item) {
            store.remove(item.itemId());

        }

        @Override
        public Optional<Item> findOne(String id) {
            return Optional.ofNullable(store.entrySet().stream().filter(i -> i.getValue().id().equals(id)).map(Map.Entry::getValue)
                    .findFirst().orElse(null));
        }
    }
}
