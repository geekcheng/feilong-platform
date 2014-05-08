/**
 * Copyright (C) 2008 feilong (venusdrogon@163.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.feilong.netpay.adaptor.doku;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.feilong.commons.core.date.DatePattern;
import com.feilong.commons.core.date.DateUtil;
import com.feilong.commons.core.enumeration.CharsetType;
import com.feilong.commons.core.enumeration.HttpMethodType;
import com.feilong.commons.core.security.oneway.SHA1Util;
import com.feilong.commons.core.util.NumberUtil;
import com.feilong.commons.core.util.RegexPattern;
import com.feilong.commons.core.util.RegexUtil;
import com.feilong.commons.core.util.Validator;
import com.feilong.netpay.adaptor.AbstractPaymentAdaptor;
import com.feilong.netpay.adaptor.doku.command.DokuQueryResult;
import com.feilong.netpay.adaptor.doku.command.Resultmsg;
import com.feilong.netpay.adaptor.doku.util.DokuQueryResultParse;
import com.feilong.netpay.adaptor.sprintasia.creditcard.command.TransactionStatus;
import com.feilong.netpay.command.PayRequest;
import com.feilong.netpay.command.PaySoLine;
import com.feilong.netpay.command.PaymentFormEntity;
import com.feilong.netpay.command.PaymentResult;
import com.feilong.netpay.command.QueryRequest;
import com.feilong.netpay.command.QueryResult;
import com.feilong.netpay.command.TradeRole;
import com.feilong.servlet.http.RequestUtil;
import com.feilong.tools.json.JsonUtil;
import com.feilong.tools.net.httpclient3.HttpClientConfig;
import com.feilong.tools.net.httpclient3.HttpClientUtil;
import com.feilong.tools.net.httpclient3.HttpClientException;

/**
 * Doku支付方式.
 * 
 * @author <a href="mailto:venusdrogon@163.com">金鑫</a>
 * @version 1.0 Mar 11, 2014 5:05:30 PM
 */
public abstract class AbstractDokuPayAdaptor extends AbstractPaymentAdaptor{

	/** The Constant log. */
	private static final Logger	log	= LoggerFactory.getLogger(AbstractDokuPayAdaptor.class);

	/** 表单提交地址. */
	private String				gateway;

	/** The method. */
	private String				method;

	/** 查询网关提交地址. */
	private String				queryGateway;

	/** queryMethod. */
	private String				queryMethod;

	/** The MALLID. */
	private String				MALLID;

	/** The CHAINMERCHANT. */
	private String				CHAINMERCHANT;

	/** The Shared_key. */
	private String				Shared_key;

	/** The CURRENCY. */
	private String				CURRENCY;

	/**
	 * Payment Channel<br>
	 * CODE DESCRIPTION
	 * <ul>
	 * <li>01 Credit Card Visa/Master</li>
	 * <li>02 Mandiri ClickPay</li>
	 * <li>05 Permata VA LITE</li>
	 * <li>06 BRI e-Pay</li>
	 * </ul>
	 * .
	 */
	private String				PAYMENTCHANNEL;

	/** The price pattern. */
	private String				pricePattern;

	/** 跳转回来带的 成功状态 code. */
	private String				redirectSuccessStatusCode;

	// Andi 拿出的 DOKU 邮件里面的script 是 "ISO-8859-1"
	/** The charset name for sh a1. */
	private String				charsetNameForSHA1;

	/** The shipping item name. */
	private String				shippingItemName;

	// alpha numeric space
	// **********************************************************************************************

	/*
	 * (non-Javadoc)
	 * @see com.feilong.netpay.adaptor.PaymentAdaptor#getPaymentFormEntity(com.feilong.netpay.command.PayRequest, java.util.Map)
	 */
	public PaymentFormEntity getPaymentFormEntity(PayRequest payRequest,Map<String, String> specialSignMap){
		doCommonValidate(payRequest);

		// ******************* 验证 code********************
		String tradeNo = payRequest.getTradeNo();
		if (tradeNo.length() > 14){ // TRANSIDMERCHANT AN …14 Transaction ID from Merchant
			throw new IllegalArgumentException("the length of code not more than 14,length is :" + tradeNo.length());
		}else if (!RegexUtil.match(RegexPattern.AN, tradeNo)){// AN
			throw new IllegalArgumentException("the tradeNo:" + tradeNo + ",must be only with numeric and alpha.");
		}

		// ******************* 验证 totalFee********************
		BigDecimal totalFee = payRequest.getTotalFee();
		BigDecimal transferFee = payRequest.getTransferFee();

		if (Validator.isNullOrEmpty(transferFee)){
			throw new IllegalArgumentException("transferFee can't be null/empty!");
		}

		// AMOUNT N 12.2 Total amount. Eg 10000.00
		BigDecimal minValue = new BigDecimal("0.00");
		// BigDecimal maxValue = new BigDecimal("999999999999.00");

		if (transferFee.compareTo(minValue) < 0){
			throw new IllegalArgumentException("transferFee:" + transferFee + " can't be less than minValue:" + minValue + "");
		}

		// ******************* 验证 totalActual********************
		String AMOUNT = NumberUtil.toString(totalFee, pricePattern);
		// these are the total amount that the buyer must paid, both of them must have the same value for checking purpose in Doku side
		String PURCHASEAMOUNT = AMOUNT;
		String REQUESTDATETIME = DateUtil.date2String(new Date(), DatePattern.timestamp);

		// see Doku_OneCheckout_Metraplasa page 61
		String PURCHASECURRENCY = CURRENCY;

		String SESSIONID = payRequest.getBuyerName();
		// ******************* 验证 NAME********************
		// NAME AN …50 Travel Arranger Name / Buyer name
		String NAME = payRequest.getBuyerName();
		if (Validator.isNullOrEmpty(NAME)){
			throw new IllegalArgumentException("NAME can't be null/empty!");
		}else if (NAME.length() > 50){
			throw new IllegalArgumentException("the length of NAME:" + NAME + " not more than 50,length is :" + NAME.length());
		}else if (!RegexUtil.match(RegexPattern.AN, NAME)){// AN
			throw new IllegalArgumentException("the NAME:" + NAME + ",must be only with numeric and alpha.");
		}

		// ******************* 验证 EMAIL********************
		String EMAIL = payRequest.getBuyerEmail();
		// EMAIL ANS …100 Customer email
		if (Validator.isNullOrEmpty(EMAIL)){
			throw new IllegalArgumentException("EMAIL can't be null/empty!");
		}else if (EMAIL.length() > 100){
			throw new IllegalArgumentException("the length of EMAIL not more than 100,length is :" + EMAIL.length());
		}
		// else if (!RegexUtil.match(RegexPattern.EMAIL, EMAIL)){
		// throw new IllegalArgumentException("the length of code not more than 14,length is :" + tradeNo.length());
		// }
		// TODO email format

		// **************************************************************************************

		Map<String, String> map = new HashMap<String, String>();

		// Given by DOKU
		map.put("MALLID", MALLID);

		// Given by DOKU, if not using Chain, default value is NA
		map.put("CHAINMERCHANT", CHAINMERCHANT);

		// Total amount. Eg 10000.00
		map.put("AMOUNT", AMOUNT);

		// Total amount. Eg: 10000.00
		map.put("PURCHASEAMOUNT", PURCHASEAMOUNT);

		// Transaction ID from Merchant

		// TRANSIDMERCHANT AN …14 Transaction ID from Merchant
		String TRANSIDMERCHANT = tradeNo;
		map.put("TRANSIDMERCHANT", TRANSIDMERCHANT);

		// Hashed key combi- nation encryption (use SHA1 meth- od).
		// The hashed key generated from combining these parameters value in this order : AMOUNT+MALLID + <shared key> +
		// TRANSIDMER-CHANT
		map.put("WORDS", getWORDSForPaymentRequest(TRANSIDMERCHANT, AMOUNT));

		// YYYYMMDDHHMMSS
		map.put("REQUESTDATETIME", REQUESTDATETIME);

		// ISO3166 , numeric code
		map.put("CURRENCY", CURRENCY);

		// ISO3166 , numeric code
		map.put("PURCHASECURRENCY", PURCHASECURRENCY);

		//
		map.put("SESSIONID", SESSIONID);

		// Travel Arranger Name / Buyer name
		map.put("NAME", NAME);

		// Customer email
		map.put("EMAIL", EMAIL);

		// Custom additional data for specific Merchant use
		// String ADDITIONALDATA = "mp2";
		// map.put("ADDITIONALDATA", ADDITIONALDATA);
		//
		// See payment chan-nel code list
		map.put("PAYMENTCHANNEL", PAYMENTCHANNEL);
		//
		// // Credit Card Number or Mandiri Debit Card
		// map.put("CARDNUMBER", "");
		//
		// // Credit Card Expiry Date (YYMM)
		// map.put("EXPIRYDATE", "");
		//
		// // Credit Card Security Number f“r "not pres”nt" transac- tions
		// map.put("CVV2", "");
		//
		// // Acquirer code for installment
		// map.put("INSTALLMENT_ACQUIRER", "");
		//
		// // Number of month to pay the installment
		// map.put("TENOR", "");
		//
		// // Promotion ID from the bank for curren merchant
		// map.put("PROMOID", "");
		//
		// // 10 last digits of card number
		// map.put("CHALLENGE_CODE_1", "");
		//
		// // amount to be paid (without fragment)
		// map.put("CHALLENGE_CODE_2", "");
		//
		// // 9 digit of random number generated by merchant
		// map.put("CHALLENGE_CODE_3", "");
		//
		// // Virtual Account identifier for VA transaction
		// map.put("PAYMENTCODE", "");

		// **********************CARDHOLDER INFOR- MATION *******************************************************
		// // Cardholder Name
		// map.put("CC_NAME", "");
		//
		// // Home address con- tains street and number
		// map.put("ADDRESS", "");
		//
		// // City name
		// map.put("CITY", "");
		//
		// // State / province name
		// map.put("STATE", "");
		//
		// // ISO3166 , alpha-2
		// map.put("COUNTRY", "");
		//
		// // Zip Code
		// map.put("ZIPCODE", "");
		//
		// // Home Phone
		// map.put("HOMEPHONE", "");
		//
		// // Mobile Phone
		// map.put("MOBILEPHONE", "");
		//
		// // Work Phone / Office Phone
		// map.put("WORKPHONE", "");
		//
		// // YYYYMMDD
		// map.put("BIRTHDATE", "");

		// *************************NON AIRLINES CATEGORY ***************************************************
		// Show transaction description.
		// Use comma to separate each field and semicolon for each item.
		// Item1,1000.00,2,20000.00;item2,15000.00,2,30000.00
		String BASKET = getBASKET(payRequest);
		map.put("BASKET", BASKET);

		// // Shipping address contains street and number
		// map.put("SHIPPING_ADDRESS", "shanghai jiangsu nan 111");
		//
		// // City name
		// map.put("SHIPPING_CITY", "shanghai");
		//
		// // State / province name
		// map.put("SHIPPING_STATE", "shanghai");
		//
		// // ISO3166 , alpha-2
		// map.put("SHIPPING_COUNTRY", "china");
		//
		// // Zip Code
		// map.put("SHIPPING_ZIPCODE", "216000");

		// log.debug("{}", JsonUtil.format(map));

		boolean isPassValidatorSpecialSignMap = validatorSpecialSignMap(specialSignMap);
		if (isPassValidatorSpecialSignMap){
			// *************************************************************************************************
			// 需要被签名的 参数map
			// 特殊 传入
			if (Validator.isNotNullOrEmpty(specialSignMap)){
				map.putAll(specialSignMap);
			}
			return getPaymentFormEntity(gateway, method, map);
		}
		throw new IllegalArgumentException("specialSignMap has IllegalArgument key");
	}

	/*
	 * (non-Javadoc)
	 * @see com.feilong.netpay.adaptor.AbstractPaymentAdaptor#getQueryResult(com.feilong.netpay.command.QueryRequest)
	 */
	public QueryResult getQueryResult(QueryRequest queryRequest) throws Exception{
		String TRANSIDMERCHANT = queryRequest.getTradeNo();

		Map<String, String> map = new HashMap<String, String>();
		map.put("MALLID", MALLID); // Given by DOKU
		map.put("CHAINMERCHANT", CHAINMERCHANT);// Given by DOKU
		map.put("TRANSIDMERCHANT", TRANSIDMERCHANT);// Transaction ID from Merchant
		map.put("SESSIONID", "" + queryRequest.getBuyer());
		map.put("PAYMENTCHANNEL", PAYMENTCHANNEL);// See payment channel code list
		// WORDS AN ...200 Hashed key combination encryption (use SHA1 meth- od).
		// The hashed key generated from combining these parameters value in this order : MALLID+<shared key>+TRANSIDMERCHANT
		map.put("WORDS", getWORDSForCheckStatus(TRANSIDMERCHANT));

		HttpClientConfig httpClientConfig = new HttpClientConfig();

		httpClientConfig.setUri(queryGateway);
		httpClientConfig.setHttpMethodType(HttpMethodType.getHttpMethodType(queryMethod));
		httpClientConfig.setParams(map);

		// // <?xml
		// version="1.0"?><PAYMENT_STATUS><AMOUNT>7790000.00</AMOUNT><TRANSIDMERCHANT>010003660001</TRANSIDMERCHANT><WORDS>e9e6ed65c872f1646644001f1b67fc8bc5de8df6</WORDS><RESPONSECODE>0000</RESPONSECODE><APPROVALCODE>RB1234567890</APPROVALCODE><RESULTMSG>SUCCESS</RESULTMSG><PAYMENTCHANNEL>06</PAYMENTCHANNEL><PAYMENTCODE></PAYMENTCODE><SESSIONID>20140508105926</SESSIONID><BANK>BRI</BANK><MCN></MCN><PAYMENTDATETIME>20140508095526</PAYMENTDATETIME><VERIFYID></VERIFYID><VERIFYSCORE>-1</VERIFYSCORE><VERIFYSTATUS>NA</VERIFYSTATUS></PAYMENT_STATUS>
		String responseBodyAsString = HttpClientUtil.getResponseBodyAsString(httpClientConfig);

		if (Resultmsg.FAILED.equals(responseBodyAsString)){
			log.error("responseBodyAsString:[{}],httpClientConfig:{}", responseBodyAsString, JsonUtil.format(httpClientConfig));
			return null;
		}

		DokuQueryResultParse dokuQueryResultParse = new DokuQueryResultParse();
		DokuQueryResult dokuQueryResult = dokuQueryResultParse.parseXML(responseBodyAsString);
		PaymentResult paymentResult = toPaymentResult(dokuQueryResult);

		// DOKU 取不到
		String paymentGatewayTradeNo = null;

		QueryResult queryResult = new QueryResult();

		queryResult.setGatewayAmount(new BigDecimal(dokuQueryResult.getAmount()));
		// 20140508095526
		queryResult.setGatewayPaymentTime(DateUtil.string2Date(dokuQueryResult.getPaymentDateTime(), DatePattern.timestamp));
		queryResult.setGatewayResult(responseBodyAsString);
		queryResult.setGatewayTradeNo(paymentGatewayTradeNo);
		queryResult.setPaymentResult(paymentResult);
		queryResult.setQueryResultCommand(dokuQueryResult);
		queryResult.setTradeNo(TRANSIDMERCHANT);

		if (log.isDebugEnabled()){
			log.debug("queryResult:{}", JsonUtil.format(queryResult));
		}
		return queryResult;
	}

	/**
	 * To payment result.
	 * 
	 * @param dokuQueryResult
	 *            the doku query result
	 * @return the payment result
	 */
	private PaymentResult toPaymentResult(DokuQueryResult dokuQueryResult){
		String resultmsg = dokuQueryResult.getResultmsg();

		// 成功
		if (Resultmsg.SUCCESS.equals(resultmsg)){
			return PaymentResult.PAID;
		}
		// 失败
		else if (Resultmsg.FAILED.equals(resultmsg)){
			return PaymentResult.NO_PAID;
		}
		// 其余抛出异常
		else{
			throw new UnsupportedOperationException("resultmsg:" + resultmsg + " not support!");
		}
	}

	/**
	 * 生成 BASKET,显示交易说明（分号分隔每个item，逗号分隔每个字段）.
	 * 
	 * @param payRequest
	 *            the pay so
	 * @return the bASKET
	 */
	private String getBASKET(PayRequest payRequest){
		StringBuilder sb = new StringBuilder();

		List<PaySoLine> paySoLineList = payRequest.getPaySoLineList();

		for (int i = 0, j = paySoLineList.size(); i < j; ++i){
			PaySoLine paySoLine = paySoLineList.get(i);

			String itemName = paySoLine.getItemName();

			itemName = formatItemName(itemName);
			sb.append(itemName);
			sb.append(",");

			sb.append(NumberUtil.toString(paySoLine.getUnitPrice(), pricePattern));
			sb.append(",");

			sb.append(paySoLine.getQuantity());
			sb.append(",");

			sb.append(NumberUtil.toString(paySoLine.getSubTotalPrice(), pricePattern));
			if (i != j - 1){
				sb.append(";");
			}
		}

		// **************如果包含运费,*****************************
		BigDecimal transferFee = payRequest.getTransferFee();
		boolean isHasTransferFee = (transferFee.compareTo(new BigDecimal(0)) == 1);

		if (isHasTransferFee){
			sb.append(";");

			sb.append(shippingItemName);
			sb.append(",");

			sb.append(NumberUtil.toString(transferFee, pricePattern));
			sb.append(",");

			sb.append(1);
			sb.append(",");

			sb.append(NumberUtil.toString(transferFee, pricePattern));
		}

		return sb.toString();
	}

	/**
	 * 将item 名称中的 分号(;) 以及逗号 (,) 过滤掉.
	 * 
	 * @param itemName
	 *            the item name
	 * @return the string
	 */
	private String formatItemName(String itemName){
		// TODO 验证特殊字符
		return itemName.replace(";", " ").replace(",", " ");
	}

	/*
	 * (non-Javadoc)
	 * @see com.jumbo.brandstore.payment.PaymentAdaptor#notifyVerify(java.lang.String, javax.servlet.http.HttpServletRequest)
	 */
	@Override
	public PaymentResult verifyNotify(HttpServletRequest request){
		// N 12.2 Total amount. Eg: 10000.00
		String AMOUNT = request.getParameter("AMOUNT");

		// AN ...14 Transaction ID from Merchant
		String TRANSIDMERCHANT = request.getParameter("TRANSIDMERCHANT");

		// AN ...200 Hashed key combination encryption (use SHA1 method).
		// The hashed key generated from com-bining these parameters value in this order:AMOUNT+MALLID+<shared key>+TRANSIDMERCHANT+
		// RESULTMSG+VERIFYSTATUS
		String WORDS = request.getParameter("WORDS");

		// A 1 P: Notify Payment V: Notify Reversal
		String STATUSTYPE = request.getParameter("STATUSTYPE");

		// N 4 0000: Success, others Failed
		String RESPONSECODE = request.getParameter("RESPONSECODE");

		// AN ...20 Transaction number from bank
		String APPROVALCODE = request.getParameter("APPROVALCODE");

		// A ...20 SUCCESS / FAILED
		String RESULTMSG = request.getParameter("RESULTMSG");

		// N 2 See payment channel code list
		String PAYMENTCHANNEL = request.getParameter("PAYMENTCHANNEL");

		// N …8 Virtual Account identifier for VA transaction
		String PAYMENTCODE = request.getParameter("PAYMENTCODE");

		// AN ...48
		String SESSIONID = request.getParameter("SESSIONID");

		// AN …100 Bank Issuer
		String BANK = request.getParameter("BANK");

		// N 14 YYYYMMDDHHMMSS
		String PAYMENTDATETIME = request.getParameter("PAYMENTDATETIME");

		// A …10 APPROVE / REJECT / REVIEW / HIGHRISK / NA
		String VERIFYSTATUS = request.getParameter("VERIFYSTATUS");

		// ANS 16 Masked card number
		// for Visa/Master
		// String MCN = request.getParameter("MCN");

		// N 30 Generated by Fraud Screening (RequestID)
		// for Visa/Master
		// String VERIFYID = request.getParameter("VERIFYID");

		// N …3 0 - 100
		// for Visa/Master
		// String VERIFYSCORE = request.getParameter("VERIFYSCORE");
		String ourWORDS = getWORDSForNotify(TRANSIDMERCHANT, AMOUNT, RESULTMSG, VERIFYSTATUS);
		boolean isSignOk = ourWORDS.equals(WORDS);

		if (isSignOk){
			log.info("signOk,tradeNo:[{}]");

			// *) main identifier of transaction success / failed
			boolean statusSuccess = Resultmsg.SUCCESS.equals(RESULTMSG);

			Object[] logArgs = { TRANSIDMERCHANT, PAYMENTCHANNEL, RESPONSECODE };
			if (!statusSuccess){
				log.error("not pass verifyNotify,tradeNo:[{}],PAYMENTCHANNEL:[{}],RESPONSECODE:[{}]", logArgs);
			}else{
				log.info("pass verifyNotify,tradeNo:[{}],PAYMENTCHANNEL:[{}],RESPONSECODE:[{}]", logArgs);
			}
			return statusSuccess ? PaymentResult.PAID : PaymentResult.FAIL;
		}
		log.error(
				"from DoKu WORDS is:{},ourWORDS:{},full request url is :{}",
				WORDS,
				ourWORDS,
				RequestUtil.getRequestFullURL(request, CharsetType.UTF8));

		return PaymentResult.FAIL;
	}

	/*
	 * (non-Javadoc)
	 * @see com.feilong.netpay.adaptor.AbstractPaymentAdaptor#doRedirectVerify(javax.servlet.http.HttpServletRequest)
	 */
	public PaymentResult verifyRedirect(HttpServletRequest request){
		// ************************************************
		// N 12.2 Total amount. Eg: 10000.00
		String AMOUNT = request.getParameter("AMOUNT");

		// AN ...14 Transaction ID from Merchant
		String TRANSIDMERCHANT = request.getParameter("TRANSIDMERCHANT");

		// AN ...200 Hashed key combination encryption (use SHA1 method).
		// The hashed key generated from com-bining these parameters value in this order:AMOUNT+MALLID+<shared key>+TRANSIDMERCHANT+
		// RESULTMSG+VERIFYSTATUS
		String WORDS = request.getParameter("WORDS");

		// N 4 0000: Success, others Failed
		String STATUSCODE = request.getParameter("STATUSCODE");

		// N 2 See payment channel code list
		String PAYMENTCHANNEL = request.getParameter("PAYMENTCHANNEL");

		// AN ...48
		String SESSIONID = request.getParameter("SESSIONID");

		// N …8 Virtual Account identifier for VA transaction
		String PAYMENTCODE = request.getParameter("PAYMENTCODE");

		String ourWORDS = getWORDSForRedirect(TRANSIDMERCHANT, AMOUNT, STATUSCODE);
		boolean isSignOk = ourWORDS.equals(WORDS);
		if (isSignOk){
			log.info("signOk,tradeNo:[{}]", TRANSIDMERCHANT);

			boolean statusSuccess = validateRedirectStatusParam(STATUSCODE);

			Object[] logArgs = { TRANSIDMERCHANT, PAYMENTCHANNEL, STATUSCODE };
			if (!statusSuccess){
				log.error("not pass verifyRedirect,tradeNo:[{}],PAYMENTCHANNEL:[{}],STATUSCODE:[{}]", logArgs);
			}else{
				log.info("pass verifyRedirect,tradeNo:[{}],PAYMENTCHANNEL:[{}],STATUSCODE:[{}]", logArgs);
			}
			return statusSuccess ? PaymentResult.PAID : PaymentResult.FAIL;
		}
		log.error(
				"from DoKu WORDS is:{},ourWORDS:{},full request url is :{}",
				WORDS,
				ourWORDS,
				RequestUtil.getRequestFullURL(request, CharsetType.UTF8));
		return PaymentResult.FAIL;
	}

	/**
	 * 验证redirect status 参数(可以被子类继承 实现,比如ATM的code 是 5511).
	 * 
	 * @param STATUSCODE
	 *            the sTATUSCODE
	 * @return true, if successful
	 */
	protected boolean validateRedirectStatusParam(String STATUSCODE){
		boolean statusSuccess = redirectSuccessStatusCode.equals(STATUSCODE);
		return statusSuccess;
	}

	/*
	 * (non-Javadoc)
	 * @see com.feilong.netpay.adaptor.PaymentAdaptor#doGetFeedbackSoCode(javax.servlet.http.HttpServletRequest)
	 */
	@Override
	public String getFeedbackTradeNo(HttpServletRequest request){
		return request.getParameter("TRANSIDMERCHANT");
	}

	/*
	 * (non-Javadoc)
	 * @see com.feilong.netpay.adaptor.PaymentAdaptor#doGetFeedbackTotalFee(javax.servlet.http.HttpServletRequest)
	 */
	public String getFeedbackTotalFee(HttpServletRequest request){
		return request.getParameter("AMOUNT");
	}

	/*
	 * (non-Javadoc)
	 * @see com.jumbo.brandstore.payment.PaymentAdaptor#closeTrade(java.lang.String, com.jumbo.brandstore.payment.TradeRole)
	 */
	public boolean closeTrade(String orderNo,TradeRole tradeRole) throws HttpClientException{
		throw new UnsupportedOperationException("DOKU don't support close trade");
	}

	/*
	 * (non-Javadoc)
	 * @see com.jumbo.brandstore.payment.PaymentAdaptor#isSupportCloseTrade()
	 */
	public boolean isSupportCloseTrade(){
		return false;
	}

	/**
	 * 验证输入的参数(子类可以按照需要 重写).
	 * 
	 * @param specialSignMap
	 *            指定的签名map
	 * @return true, if successful
	 */
	protected boolean validatorSpecialSignMap(Map<String, String> specialSignMap){
		return true;
	}

	/**
	 * Hashed key combination encryption (use SHA1 method). <br>
	 * The hashed key generated from com- bining these parameters value in this order: <br>
	 * AMOUNT+MALLID+<shared key> +TRANSIDMERCHANT+ RESULTMSG+VERIFYSTATUS
	 * 
	 * @param TRANSIDMERCHANT
	 *            the tRANSIDMERCHANT
	 * @param AMOUNT
	 *            the aMOUNT
	 * @return the wORDS
	 */
	private String getWORDSForPaymentRequest(String TRANSIDMERCHANT,String AMOUNT){
		String WORDS = AMOUNT + MALLID + Shared_key + TRANSIDMERCHANT;
		return SHA1Util.encode(WORDS, charsetNameForSHA1);
	}

	/**
	 * Hashed key combination encryption (use SHA1 method).<br>
	 * The hashed key generated from com- bining these parameters value in this order:<br>
	 * AMOUNT+MALLID+<shared key> +TRANSIDMERCHANT+ RESULTMSG+VERIFYSTATUS
	 * 
	 * @param TRANSIDMERCHANT
	 *            the tRANSIDMERCHANT
	 * @param AMOUNT
	 *            the aMOUNT
	 * @param RESULTMSG
	 *            the rESULTMSG
	 * @param VERIFYSTATUS
	 *            the vERIFYSTATUS
	 * @return the wORDS for notify
	 */
	private String getWORDSForNotify(String TRANSIDMERCHANT,String AMOUNT,String RESULTMSG,String VERIFYSTATUS){
		String WORDS = AMOUNT + MALLID + Shared_key + TRANSIDMERCHANT + RESULTMSG + VERIFYSTATUS;
		return SHA1Util.encode(WORDS, charsetNameForSHA1);
	}

	/**
	 * Gets the wORDS for check status.
	 * 
	 * @param TRANSIDMERCHANT
	 *            the tRANSIDMERCHANT
	 * @return the wORDS for check status
	 */
	// Hashed key combination encryption (use SHA1 meth- od).
	// The hashed key generated from combining these parameters value in this order : MALLID+<shared key>+TRANSIDMERCHANT
	private String getWORDSForCheckStatus(String TRANSIDMERCHANT){
		String WORDS = MALLID + Shared_key + TRANSIDMERCHANT;
		return SHA1Util.encode(WORDS, charsetNameForSHA1);
	}

	/**
	 * Hashed key combination encryption (use SHA1 method). <br>
	 * The hashed key generated from combining these parameters value in this order:<br>
	 * AMOUNT+<shared key> +TRANSIDMERCHANT+STATUSCODE
	 * 
	 * @param TRANSIDMERCHANT
	 *            the tRANSIDMERCHANT
	 * @param AMOUNT
	 *            the aMOUNT
	 * @param STATUSCODE
	 *            the sTATUSCODE
	 * @return the wORDS for redirect
	 */
	private String getWORDSForRedirect(String TRANSIDMERCHANT,String AMOUNT,String STATUSCODE){
		String WORDS = AMOUNT + Shared_key + TRANSIDMERCHANT + STATUSCODE;
		return SHA1Util.encode(WORDS, charsetNameForSHA1);
	}

	// ****************************************************************************************************************************

	/**
	 * Sets the 表单提交地址 <br>
	 * 支付宝网关.
	 * 
	 * @param gateway
	 *            the gateway to set
	 */
	public void setGateway(String gateway){
		this.gateway = gateway;
	}

	/**
	 * Sets the mALLID.
	 * 
	 * @param mALLID
	 *            the mALLID to set
	 */
	public void setMALLID(String mALLID){
		MALLID = mALLID;
	}

	/**
	 * Sets the cHAINMERCHANT.
	 * 
	 * @param cHAINMERCHANT
	 *            the cHAINMERCHANT to set
	 */
	public void setCHAINMERCHANT(String cHAINMERCHANT){
		CHAINMERCHANT = cHAINMERCHANT;
	}

	/**
	 * Sets the shared_key.
	 * 
	 * @param shared_key
	 *            the shared_key to set
	 */
	public void setShared_key(String shared_key){
		Shared_key = shared_key;
	}

	/**
	 * Sets the method.
	 * 
	 * @param method
	 *            the method to set
	 */
	public void setMethod(String method){
		this.method = method;
	}

	/**
	 * Sets the shipping item name.
	 * 
	 * @param shippingItemName
	 *            the shippingItemName to set
	 */
	public void setShippingItemName(String shippingItemName){
		this.shippingItemName = shippingItemName;
	}

	/**
	 * Sets the pAYMENTCHANNEL.
	 * 
	 * @param pAYMENTCHANNEL
	 *            the pAYMENTCHANNEL to set
	 */
	public void setPAYMENTCHANNEL(String pAYMENTCHANNEL){
		PAYMENTCHANNEL = pAYMENTCHANNEL;
	}

	/**
	 * Sets the price pattern.
	 * 
	 * @param pricePattern
	 *            the pricePattern to set
	 */
	public void setPricePattern(String pricePattern){
		this.pricePattern = pricePattern;
	}

	/**
	 * Sets the cURRENCY.
	 * 
	 * @param cURRENCY
	 *            the cURRENCY to set
	 */
	public void setCURRENCY(String cURRENCY){
		CURRENCY = cURRENCY;
	}

	/**
	 * Sets the 跳转回来带的 成功状态 code.
	 * 
	 * @param redirectSuccessStatusCode
	 *            the redirectSuccessStatusCode to set
	 */
	public void setRedirectSuccessStatusCode(String redirectSuccessStatusCode){
		this.redirectSuccessStatusCode = redirectSuccessStatusCode;
	}

	/**
	 * Sets the charset name for sh a1.
	 * 
	 * @param charsetNameForSHA1
	 *            the charsetNameForSHA1 to set
	 */
	public void setCharsetNameForSHA1(String charsetNameForSHA1){
		this.charsetNameForSHA1 = charsetNameForSHA1;
	}

	/**
	 * 设置 查询网关提交地址.
	 * 
	 * @param queryGateway
	 *            the queryGateway to set
	 */
	public void setQueryGateway(String queryGateway){
		this.queryGateway = queryGateway;
	}

	/**
	 * 设置 queryMethod.
	 * 
	 * @param queryMethod
	 *            the queryMethod to set
	 */
	public void setQueryMethod(String queryMethod){
		this.queryMethod = queryMethod;
	}

}
