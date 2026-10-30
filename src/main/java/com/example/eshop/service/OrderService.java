package com.example.eshop.service;

import com.example.eshop.cart.Cart;
import com.example.eshop.cart.CartItem;
import com.example.eshop.dto.CheckoutForm;
import com.example.eshop.entity.*;
import com.example.eshop.exception.NotFoundException;
import com.example.eshop.repository.OrderRepository;
import com.example.eshop.repository.ProductRepository;
import com.example.eshop.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    private final UserRepository userRepository;

    /**
     * Tạo đơn hàng từ giỏ. Toàn bộ trong 1 transaction:
     * nếu 1 sản phẩm không đủ hàng → ném exception → rollback, không trừ kho sản phẩm nào.
     */
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional
    public Order placeOrder(CheckoutForm form, Cart cart, String username) {
        if (cart.isEmpty()) {
            throw new IllegalStateException("Giỏ hàng đang trống");
        }
        Order order = new Order();
        order.setCustomerName(form.getCustomerName());
        order.setPhone(form.getPhone());
        order.setAddress(form.getAddress());
        order.setNote(form.getNote());
        order.setPaymentMethod(form.getPaymentMethod());
        order.setStatus(OrderStatus.NEW);

        order.setUser(userRepository.findByUsername(username).orElse(null));

        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : cart.getItems()) {
            Product p = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new IllegalStateException("Sản phẩm \"" + item.getName() + "\" không còn bán"));
            if (p.getQuantity() < item.getQuantity()) {
                throw new IllegalStateException("\"" + p.getName() + "\" chỉ còn " + p.getQuantity() + " sản phẩm");
            }
            p.setQuantity(p.getQuantity() - item.getQuantity());   // dirty checking → UPDATE product

            OrderDetail d = new OrderDetail();
            d.setProduct(p);
            d.setQuantity(item.getQuantity());
            d.setUnitPrice(p.getPrice());                          // giá lấy từ DB, không tin giá trong giỏ
            order.addDetail(d);
            total = total.add(d.getSubtotal());
        }
        order.setTotalAmount(total);
        return orderRepository.save(order);
    }

    public Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng #" + id));
    }

    public List<Order> findAll() {
        return orderRepository.findAllByOrderByOrderDateDesc();
    }

    /** Giả lập cổng thanh toán xác nhận đã nhận tiền. */
    @Transactional
    public void markPaid(Long id) {
        Order o = findById(id);
        if (o.getStatus() == OrderStatus.NEW) {
            o.setStatus(OrderStatus.PAID);
        }
    }

    @PreAuthorize("hasRole('SELLER')")
    @Transactional
    public void updateStatus(Long id, OrderStatus status) {
        Order o = findById(id);
        // Huỷ đơn → hoàn lại tồn kho
        if (status == OrderStatus.CANCELLED && o.getStatus() != OrderStatus.CANCELLED) {
            o.getDetails().forEach(d -> d.getProduct().setQuantity(d.getProduct().getQuantity() + d.getQuantity()));
        }
        o.setStatus(status);
    }

    @PreAuthorize("hasRole('SELLER')")
    @Transactional
    public void delete(Long id) {
        orderRepository.delete(findById(id));   // cascade xoá order_detail
    }

    public List<Order> findByUsername(String username) {
        return orderRepository.findByUserUsernameOrderByOrderDateDesc(username);
    }

}

