package com.qvc.shoppingcart.space;

import com.gigaspaces.document.DocumentProperties;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qvc.shoppingcart.common.Address;
import com.qvc.shoppingcart.common.BillingAddress;
import com.qvc.shoppingcart.common.Cart;
import com.qvc.shoppingcart.common.CartTracker;
import com.qvc.shoppingcart.common.Cost;
import com.qvc.shoppingcart.common.LineItem;
import com.qvc.shoppingcart.common.PaymentData;
import com.qvc.shoppingcart.common.ShippingAddress;
import com.qvc.shoppingcart.service.ICartService;
import com.qvc.shoppingcart.service.ICartTrackerService;
import org.openspaces.core.GigaSpace;
import org.openspaces.core.transaction.manager.DistributedJiniTxManagerConfigurer;
import org.openspaces.remoting.RemotingService;
import org.openspaces.remoting.Routing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

// This lives within each partition
@RemotingService
@Transactional
public class CartService implements ICartService {

  @Autowired
  GigaSpace gigaSpace;

  @Autowired
  ICartTrackerService cartTrackerService;

  public String getCart(@Routing long cartId) {
    System.out.printf("GETTING CART [%d]\n", cartId);
    Cart cart = gigaSpace.readById(Cart.class, cartId);
    Gson gson = new Gson();
    String cartJson = gson.toJson(cart);
    System.out.printf("cartJson = %s\n", cartJson);
    return cartJson;
  }

  @Override
  public boolean isCartExist(long cartId) {
    return false;
  }

  @Override
  public boolean updateCart(@Routing long cartId, String cartJson) {
    boolean rv = false;
    try {
      PlatformTransactionManager ptm = new DistributedJiniTxManagerConfigurer().transactionManager();
      DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
      definition.setPropagationBehavior(Propagation.REQUIRES_NEW.ordinal());
      TransactionStatus status = ptm.getTransaction(definition);
      try {
        // TODO: examine the contents of cartJson and update the cart
        System.out.printf("Cart [%d] modified using: %s\n", cartId, cartJson);
        CartTracker cartTracker = gigaSpace.readById(CartTracker.class, cartId);
        long currentTimestamp = System.currentTimeMillis();
        cartTracker.setLastUpdateTimestamp(currentTimestamp);
        gigaSpace.write(cartTracker);
        ptm.commit(status);
        rv = true;
      } catch(Exception e) {
        ptm.rollback(status);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return rv;
  }

  @Override
  public void updatePaymentData(long cartId, String paymentJson) {
    try {
      PlatformTransactionManager ptm = new DistributedJiniTxManagerConfigurer().transactionManager();
      DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
      definition.setPropagationBehavior(Propagation.REQUIRES_NEW.ordinal());
      TransactionStatus status = ptm.getTransaction(definition);
      try {
        System.out.printf("paymentJson: %s\n", paymentJson);
        PaymentData paymentData = new PaymentData(paymentJson);
        Cart cart = gigaSpace.readById(Cart.class, cartId);
        cart.setPaymentData(paymentData);
        gigaSpace.write(cart);
        CartTracker cartTracker = gigaSpace.readById(CartTracker.class, cartId);
        long currentTimestamp = System.currentTimeMillis();
        cartTracker.setLastUpdateTimestamp(currentTimestamp);
        gigaSpace.write(cartTracker);
        ptm.commit(status);
      } catch(Exception e) {
        ptm.rollback(status);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public boolean createCart(@Routing long id, String itemListJson) {

    try {
      PlatformTransactionManager ptm = new DistributedJiniTxManagerConfigurer().transactionManager();
      DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
      definition.setPropagationBehavior(Propagation.REQUIRES_NEW.ordinal());
      TransactionStatus status = ptm.getTransaction(definition);
      try {
        Cart cart = new Cart();
        cart.setId(id);
        JsonParser parser = new JsonParser();
        JsonObject jsonObject = (JsonObject) parser.parse(itemListJson);
        String user = jsonObject.get("user").getAsString();
        cart.setUser(user);
        JsonObject jsonShippingAddress = jsonObject.getAsJsonObject("shippingAddress");
        String streetShippingAddress = jsonShippingAddress.get(Address.STREET).getAsString();
        String cityShippingAddress = jsonShippingAddress.get(Address.CITY).getAsString();
        String countryShippingAddress = jsonShippingAddress.get(Address.COUNTRY).getAsString();
        String typeShippingAddress = jsonShippingAddress.get("type").getAsString();
        ShippingAddress shippingAddress = new ShippingAddress(streetShippingAddress, cityShippingAddress, countryShippingAddress, typeShippingAddress);
        JsonObject jsonBillingAddress = jsonObject.getAsJsonObject("billingAddress");
        String streetBillingAddress = jsonBillingAddress.get(Address.STREET).getAsString();
        String cityBillingAddress = jsonBillingAddress.get(Address.CITY).getAsString();
        String countryBillingAddress = jsonBillingAddress.get(Address.COUNTRY).getAsString();
        String billedParty = jsonBillingAddress.get("billedParty").getAsString();
        BillingAddress billingAddress = new BillingAddress(streetBillingAddress, cityBillingAddress, countryBillingAddress, billedParty);
        cart.setShippingAddress(shippingAddress);
        cart.setBillingAddress(billingAddress);
        JsonObject cost = jsonObject.getAsJsonObject("prize");
        String prizeName = cost.get("name").getAsString();
        Integer amount = cost.get("amount").getAsInt();
        BigDecimal prizeAmount = new BigDecimal(amount);
        DocumentProperties documentProperties = new DocumentProperties();
        documentProperties.setProperty("creationDate", new Date());
        documentProperties.setProperty("user", "sudip");
        Cost prize = new Cost(prizeName, prizeAmount);
        prize.setProperties(documentProperties);
        cart.setPrize(prize);
        JsonArray itemArray = jsonObject.getAsJsonArray("items");
        for (JsonElement item : itemArray) {
          JsonObject itemObject = item.getAsJsonObject();
          String name = itemObject.get(LineItem.NAME).getAsString();
          Integer count = itemObject.get(LineItem.QUANTITY).getAsInt();
          DocumentProperties dpLineItem = new DocumentProperties();
          dpLineItem.put(LineItem.NAME, name);
          dpLineItem.put(LineItem.QUANTITY, count);
          LineItem lineItem = new LineItem(id, dpLineItem);
          List<Cost> discounts = new ArrayList<>();
          JsonArray discountElements = itemObject.getAsJsonArray("discounts");
          for (JsonElement discountElement : discountElements) {
            JsonObject discountObject = discountElement.getAsJsonObject();
            String discountName = discountObject.get("name").getAsString();
            Integer amountOfDiscount = discountObject.get("amount").getAsInt();
            BigDecimal discountAmount = new BigDecimal(amountOfDiscount);
            DocumentProperties documentPropertiesOfDiscount = new DocumentProperties();
            documentPropertiesOfDiscount.setProperty("creationDate", new Date());
            documentPropertiesOfDiscount.setProperty("user", "sudip");
            Cost discount = new Cost(discountName, discountAmount);
            discounts.add(discount);
          }
          lineItem.setDiscounts(discounts);
          cart.addLineItem(lineItem);
          System.out.printf("added lineItem with id [%s]\n", lineItem.getId());
        }
        gigaSpace.write(cart);
        long currentTimestamp = System.currentTimeMillis();
        System.out.printf("Cart [%d] created using: %s\n", id, itemListJson);
        CartTracker cartTracker = new CartTracker(id, currentTimestamp);
        gigaSpace.write(cartTracker);
        ptm.commit(status);
      } catch (Exception e) {
        ptm.rollback(status);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return true;
  }

  @Override
  public void scan() {
    cartTrackerService.scan();
  }

  @Override
  public void touch(long cartId) {
    cartTrackerService.touch(cartId);
  }
}
