package com.qvc.shoppingcart.space;

import com.j_spaces.core.client.SQLQuery;
import com.qvc.shoppingcart.common.Cart;
import com.qvc.shoppingcart.common.CartTracker;
import com.qvc.shoppingcart.service.ICartTrackerService;
import org.openspaces.core.GigaSpace;
import org.openspaces.core.transaction.manager.DistributedJiniTxManagerConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.DefaultTransactionDefinition;

@Component
public class CartTrackerService implements ICartTrackerService {

  public static final long LIFETIME = 1000L * 180; /* 1000L*60*60*24 = 1 day */

//  public static final long LIFETIME = 86400000; /* 1000L*60*60*24 = 1 day */

  @Autowired
  GigaSpace gigaSpace;

  @Override
  public boolean touch(long cartId) {
    CartTracker cartTracker = gigaSpace.readById(CartTracker.class, cartId);
    long currentTimestamp = System.currentTimeMillis();
    cartTracker.setLastUpdateTimestamp(currentTimestamp);
    gigaSpace.write(cartTracker);
    return false;
  }

  @Override
  public void scan() {
    long currentTimestamp = System.currentTimeMillis();
    String queryStr = "(lastUpdateTimestamp + " + LIFETIME + ") < " + Long.toString(currentTimestamp);
    System.out.printf("queryStr = [%s]\n", queryStr);
    try {
      PlatformTransactionManager ptm = new DistributedJiniTxManagerConfigurer().transactionManager();
      DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
      definition.setPropagationBehavior(Propagation.REQUIRES_NEW.ordinal());
      TransactionStatus status = ptm.getTransaction(definition);
      try {
        SQLQuery<CartTracker> query = new SQLQuery<>(CartTracker.class, queryStr);
        CartTracker[] cartTrackers = gigaSpace.takeMultiple(query);
        System.out.printf("scan query returned %d matches\n", cartTrackers.length);
        for (CartTracker cartTracker : cartTrackers) {
          long cartId = cartTracker.getCartId();
          gigaSpace.takeById(Cart.class, cartId);
        }
        ptm.commit(status);
      } catch (Exception e) {
        ptm.rollback(status);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void remove(String cartId) {
    try {
      PlatformTransactionManager ptm = new DistributedJiniTxManagerConfigurer().transactionManager();
      DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
      definition.setPropagationBehavior(Propagation.REQUIRES_NEW.ordinal());
      TransactionStatus status = ptm.getTransaction(definition);
      try {
        gigaSpace.takeById(Cart.class, cartId);
        ptm.commit(status);
      } catch (Exception e) {
        ptm.rollback(status);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
