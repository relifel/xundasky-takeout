package com.sky.service;

import com.sky.dto.ShoppingCartDTO;
import org.springframework.stereotype.Service;

public interface ShoppingCartService {
    void add(ShoppingCartDTO shoppingCartDTO);
}
