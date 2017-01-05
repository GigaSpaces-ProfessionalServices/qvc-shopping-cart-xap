package com.qvc.shoppingcart.service;

public interface ICartTrackerService {

  boolean touch(long cartId);

  void scan();

  void remove(String cartId);
}
