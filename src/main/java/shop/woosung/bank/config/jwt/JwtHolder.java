package shop.woosung.bank.config.jwt;

import shop.woosung.bank.config.auth.LoginUser;
import shop.woosung.bank.domain.user.User;

public interface JwtHolder {

    String createToken(LoginUser loginUser);

    User verifyToken(String token);
}