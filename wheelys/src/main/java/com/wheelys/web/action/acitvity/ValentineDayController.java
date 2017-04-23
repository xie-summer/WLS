package com.wheelys.web.action.acitvity;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import com.wheelys.Config;
import com.wheelys.api.vo.ResultCode;
import com.wheelys.util.DateUtil;
import com.wheelys.util.ValidateUtil;
import com.wheelys.web.util.LoginUtils;
import com.wheelys.constant.CafeOrderConstant;
import com.wheelys.model.CafeShop;
import com.wheelys.model.CafeShopProfile;
import com.wheelys.model.discount.DiscountActivity;
import com.wheelys.model.order.CardCouponsOrder;
import com.wheelys.model.order.MemberAddress;
import com.wheelys.model.user.OpenMember;
import com.wheelys.model.user.WheelysMember;
import com.wheelys.service.MemberService;
import com.wheelys.service.cafe.CafeShopProfileService;
import com.wheelys.service.cafe.CafeShopService;
import com.wheelys.service.mlink.SendMessageService;
import com.wheelys.service.order.CardCouponsOrderService;
import com.wheelys.service.order.DiscountService;
import com.wheelys.service.order.MemberAddressService;
import com.wheelys.service.order.OrderService;
import com.wheelys.service.pay.ElecCardService;
import com.wheelys.untrans.pay.WxMpPayService;
import com.wheelys.util.OAuthUtils;
import com.wheelys.util.WebUtils;
import com.wheelys.web.action.AnnotationController;
import com.wheelys.web.action.wap.vo.WheelysOrderVo;

@Controller
public class ValentineDayController extends AnnotationController {

	@Autowired@Qualifier("config")
	private Config config;
	@Autowired@Qualifier("orderService")
	private OrderService orderService;
	@Autowired@Qualifier("discountService")
	private DiscountService discountService;
	@Autowired@Qualifier("memberAddressService")
	private MemberAddressService memberAddressService;
	@Autowired@Qualifier("cafeShopProfileService")
	private CafeShopProfileService cafeShopProfileService;
	@Autowired@Qualifier("wxMpPayService")
	private WxMpPayService wxMpPayService;
	@Autowired@Qualifier("elecCardService")
	private ElecCardService elecCardService;
	@Autowired@Qualifier("memberService")
	private MemberService memberService;
	@Autowired@Qualifier("cafeShopService")
	private CafeShopService cafeShopService;
	@Autowired@Qualifier("sendMessageService")
	private SendMessageService sendMessageService;
	@Autowired@Qualifier("cardCouponsOrderService")
	private CardCouponsOrderService cardCouponsOrderService;
	
	@RequestMapping("/activity/valentine.xhtml")
	public String valentine(ModelMap model, HttpServletRequest request){
		WheelysMember member = this.getLogonMember();
		if(member == null){
			String lastpage = config.getAbsPath()+"activity/valentine.xhtml";
			String oauthUrl = OAuthUtils.oauth2Url(request, lastpage, config.getAbsPath());
			return showRedirect(oauthUrl, model);
		}
		Map<Long,String> shopmap = new LinkedHashMap<Long,String>();
		shopmap.put(13L, "上海静安嘉里中心店");
		shopmap.put(20L, "上海长泰广场店");
		shopmap.put(57L, "上海恒基名人店");
		shopmap.put(56L, "上海美罗城店");
		shopmap.put(37L, "上海新大陆广场店");
		shopmap.put(47L, "上海凯德龙之梦莘庄店");
		shopmap.put(38L, "上海新天地店");
		shopmap.put(49L, "无锡南禅寺店");
		shopmap.put(39L, "无锡万象城店");
		shopmap.put(51L, "南京江宁万达广场店");
		shopmap.put(52L, "南京茂业天地店");
		shopmap.put(41L, "杭州城西银泰店");
		shopmap.put(53L, "苏州圆融星座购物中心");
		if(2 == member.getId().intValue()){
			shopmap.put(6L, "funwork店");
		}
		model.put("shopmap", shopmap);
		model.put("member", member);
		Timestamp curtime = DateUtil.getMillTimestamp();
		Timestamp validtime = DateUtil.parseTimestamp("2017-02-10 14:59:59");
		Timestamp closetime = DateUtil.parseTimestamp("2017-02-17 14:59:59");
		model.put("isBefore", curtime.before(validtime));
		model.put("isBeforeClosetime", curtime.before(closetime));
		return "/wps/valentine/entrance.vm";
	}
	
	@RequestMapping("/valentine/topay.xhtml")
	public String pay(String captcha, String mobile, Long shopid, String datetype, ModelMap model, HttpServletRequest request, HttpServletResponse response){
		WheelysMember member = this.getLogonMember();
		member = memberService.getWheelysMemberByMemberId(member.getId());
		if(!ValidateUtil.isMobile(member.getMobile())){
			ResultCode<WheelysMember> resultCode = memberService.bindMobile(member.getId(), mobile, captcha, WebUtils.getRemoteIp(request));
			if(resultCode.isSuccess()){
				member = resultCode.getRetval();
				//更新登录状态
				memberService.updateMemberAuth(LoginUtils.getSessid(request), member);
			}else{
				return this.showJsonError(model, resultCode.getMsg());
			}
		}
		String citycode = WebUtils.getAndSetCityCode(request, response);
		String ukey = "";
		OpenMember openMember = memberService.getOpenMemberByMemberid(member.getId());
		if(openMember != null){
			ukey = openMember.getLoginname();
		}
		ResultCode<CardCouponsOrder> result = cardCouponsOrderService.createValentineOrder(member, shopid, ukey, citycode, datetype, "情人节活动",null);
		if(result.isSuccess()){
			String clientIp = WebUtils.getRemoteIp(request);
			CardCouponsOrder order = result.getRetval();
			HashMap data = new HashMap();
			data.put("paystatus", order.getPaystatus());
			data.put("tradeno", order.getTradeno());
			Map<String, String> payparams = wxMpPayService.getJsapiPayJsonParams(order , clientIp);
			data.put("payparams", payparams);
			return this.showJsonSuccess(model, data);
		}
		return this.showJsonError(model, result.getMsg());
	}
	
	@RequestMapping("/valentine/payresult.xhtml")
	public String payresult(String tradeno, ModelMap model){
		CardCouponsOrder order = cardCouponsOrderService.getOrder(tradeno);
		model.put("order", order);
		if(order != null){
			CafeShop shop = cafeShopService.getCacheShop(order.getShopid());
			model.put("shop", shop);
		}
		return "/wps/valentine/entranceComplete.vm";
	}
	
	@RequestMapping("/valentine/order.xhtml")
	public String order(String tradeno, String isedit, ModelMap model){
		WheelysMember member = this.getLogonMember();
		Long memberid = null;
		if(member != null){
			memberid = member.getId();
		}
		ResultCode<WheelysOrderVo> orderVo = orderService.getWheelysOrderVo(tradeno, memberid);
		if(orderVo.isSuccess()){
			WheelysOrderVo order = orderVo.getRetval();
			if(StringUtils.equals(order.getCategory(), CafeOrderConstant.CATEGORY_TAKEAWAY)){
				CafeShopProfile shopProfile = cafeShopProfileService.getShopProfile(order.getShopid());
				MemberAddress memberAddress = memberAddressService.getMemberAddressByMemidAndShopid(order.getMemberid(), order.getShopid());
				model.put("shopProfile", shopProfile);
				model.put("memberAddress", memberAddress);
			}
			if(order.getSdid() != null && order.getSdid() > 0){
				DiscountActivity discount = discountService.getDiscount(order.getSdid());
				model.put("discount", discount);
			}
			if(member != null){
				model.put("isedit", (order.getMemberid().intValue() == member.getId().intValue() && StringUtils.isNotBlank(isedit)));
			}
			model.put("orderVo", orderVo.getRetval());
		}
		return "/wps/valentine/toHer.vm";
	}
	
	@RequestMapping("/valentine/otherinfo.xhtml")
	public String otherinfo(String tradeno, String otherinfo, ModelMap model){
		WheelysMember member = this.getLogonMember();
		orderService.saveOntherInfo(tradeno,member.getId(),otherinfo);
		return this.showJsonSuccess(model, null);
	}
	
}
