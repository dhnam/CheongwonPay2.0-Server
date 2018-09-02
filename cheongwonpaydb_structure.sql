-- phpMyAdmin SQL Dump
-- version 4.0.10.20
-- https://www.phpmyadmin.net
--
-- 호스트: localhost
-- 처리한 시간: 18-09-02 21:22
-- 서버 버전: 5.0.95
-- PHP 버전: 5.3.3

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;

--
-- 데이터베이스: `cheongwonpaydb`
--

-- --------------------------------------------------------

--
-- 테이블 구조 `atd_history`
--

CREATE TABLE IF NOT EXISTS `atd_history` (
  `Num` int(11) NOT NULL auto_increment,
  `User_Num` int(11) NOT NULL,
  `Club_Num` int(11) NOT NULL,
  `Time` timestamp NOT NULL default CURRENT_TIMESTAMP,
  PRIMARY KEY  (`Num`)
) ENGINE=MyISAM  DEFAULT CHARSET=utf8 ROW_FORMAT=FIXED;

-- --------------------------------------------------------

--
-- 테이블 구조 `club`
--

CREATE TABLE IF NOT EXISTS `club` (
  `Club_Num` int(11) NOT NULL auto_increment,
  `Name` text NOT NULL,
  `Income` int(11) NOT NULL default '0',
  `PW` varchar(300) NOT NULL default 'fa585d89c851dd338a70dcf535aa2a92fee7836dd6aff1226583e88e0996293f16bc009c652826e0fc5c706695a03cddce372f139eff4d13959da6f1f5d3eabe' COMMENT '12345678->sha512',
  `School` char(1) NOT NULL,
  PRIMARY KEY  (`Club_Num`)
) ENGINE=MyISAM  DEFAULT CHARSET=utf8;

-- --------------------------------------------------------

--
-- 테이블 구조 `goods`
--

CREATE TABLE IF NOT EXISTS `goods` (
  `Goods_Num` int(11) NOT NULL auto_increment,
  `Club_Num` int(11) NOT NULL,
  `Goods_Name` text NOT NULL,
  `Price` int(11) NOT NULL,
  PRIMARY KEY  (`Goods_Num`)
) ENGINE=MyISAM  DEFAULT CHARSET=utf8;

-- --------------------------------------------------------

--
-- 테이블 구조 `transactions`
--

CREATE TABLE IF NOT EXISTS `transactions` (
  `Num` int(11) NOT NULL auto_increment,
  `User` text NOT NULL,
  `Club_Num` int(11) NOT NULL,
  `Goods_Num` int(11) NOT NULL default '0',
  `Cancel` varchar(5) default NULL,
  `Price` int(11) NOT NULL,
  `Time` timestamp NOT NULL default CURRENT_TIMESTAMP,
  PRIMARY KEY  (`Num`)
) ENGINE=MyISAM  DEFAULT CHARSET=utf8;

-- --------------------------------------------------------

--
-- 테이블 구조 `user`
--

CREATE TABLE IF NOT EXISTS `user` (
  `Num` int(11) NOT NULL auto_increment,
  `User` varchar(30) NOT NULL,
  `Balance` int(11) NOT NULL default '0',
  `Club_Num` int(11) NOT NULL default '4',
  `Name` varchar(100) NOT NULL default 'GUEST',
  `Grade` int(11) NOT NULL default '0',
  `Class` int(11) NOT NULL default '0',
  `Number` int(11) NOT NULL default '0',
  `School` char(1) NOT NULL default 'N',
  `Lost` int(11) NOT NULL default '0',
  `ATD_Count` int(11) NOT NULL default '0',
  PRIMARY KEY  (`Num`),
  UNIQUE KEY `User` (`User`),
  UNIQUE KEY `User_2` (`User`)
) ENGINE=MyISAM  DEFAULT CHARSET=utf8;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
