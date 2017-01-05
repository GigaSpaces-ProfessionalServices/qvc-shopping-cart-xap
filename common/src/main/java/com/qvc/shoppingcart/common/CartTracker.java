package com.qvc.shoppingcart.common;

import com.gigaspaces.annotation.pojo.SpaceClass;
import com.gigaspaces.annotation.pojo.SpaceId;
import com.gigaspaces.annotation.pojo.SpaceIndex;
import com.gigaspaces.metadata.index.SpaceIndexType;

/**
 * Created by sudip on 11/22/2016.
 */
@SpaceClass
public class CartTracker {

  private long cartId;
  private long lastUpdateTimestamp;

  @SpaceId
  public long getCartId() {
    return cartId;
  }

  public void setCartId(long cartId) {
    this.cartId = cartId;
  }

  @SpaceIndex(type = SpaceIndexType.EXTENDED)
  public long getLastUpdateTimestamp() {
    return lastUpdateTimestamp;
  }

  public void setLastUpdateTimestamp(long timestamp) {
    lastUpdateTimestamp = timestamp;
  }

  public CartTracker(long cartId, long lastUpdateTimestamp) {
    this.cartId = cartId;
    this.lastUpdateTimestamp = lastUpdateTimestamp;
  }

  public CartTracker() {
  }
}
