package no.hvl.dat110.util;

/**
 * project 3
 * @author tdoy
 *
 */

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Hash { 
	
	private static BigInteger hashint; 
	
	/*
	 * Task: Hash a given string using MD5 and return the result as a BigInteger.
	 * we use MD5 with 128 bits digest
	 * compute the hash of the input 'entity'
	 * convert the hash into hex format
	 * convert the hex into BigInteger
	 * return the BigInteger
	 */
	public static BigInteger hashOf(String entity) {		
		try {
			MessageDigest messageDigest = MessageDigest.getInstance("MD5");
			messageDigest.update(entity.getBytes());
			hashint = new BigInteger(toHex(messageDigest.digest()), 16);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return hashint;
	}
	
	/*
	 * Task: compute the address size of MD5		
	 * get the digest length
	 * compute the number of bits = digest length * 8
	 * compute the address size = 2 ^ number of bits
	 * return the address size
	 */
	@SuppressWarnings("deprecation")
	public static BigInteger addressSize() {
		try {
			MessageDigest messageDigest = MessageDigest.getInstance("MD5");
			return BigInteger.valueOf((new Double(Math.pow(2,(messageDigest.getDigestLength() * 8))).longValue()));
			
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	// find the digest length
	public static int bitSize() {
		try {
			return MessageDigest.getInstance("MD5").getDigestLength() * 8;		
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return 0;
		}
	}
	
	public static String toHex(byte[] digest) {
		StringBuilder strbuilder = new StringBuilder();
		for(byte b : digest) {
			strbuilder.append(String.format("%02x", b&0xff));
		}
		return strbuilder.toString();
	}

}
