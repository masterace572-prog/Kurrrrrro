<?php

$servername = "localhost";
$username = "aalyanza_panel";
$password = "aalyanza_panel";
$dbname = "aalyanza_panel";

$conn = mysqli_connect($servername,$username,$password,$dbname);

if(!$conn) {

die(" PROBLEM WITH CONNECTION : " . mysqli_connect_error());

}
  
?>