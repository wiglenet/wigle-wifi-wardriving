#!/usr/bin/perl -w

use strict;

#use utf8;
use URI::Escape;
use LWP::Simple;
use LWP::UserAgent;


my $msglanguage = 'italian';
$msglanguage = 'chinese';
my $output = &doX("i like to ride my bike");
print "out: $output\n";
open FILER, ">output.xml";
binmode(STDOUT, ":utf8");
binmode(FILER, ":utf8");
print FILER $output;
print FILER "\n";
close FILER;

sub doX {
  my $text = shift;
  my $ua = LWP::UserAgent->new;
  $ua->agent('Mozilla/5.0 (Macintosh; Intel Mac OS X 10.6; rv:6.0.2) Gecko/20100101 Firefox/6.0.2');
  $ua->timeout(15); # 15s timeout

  my $language;
  $language = "en_zh" if $msglanguage =~ /chinese/si;
  $language = "en_zt" if $msglanguage =~ /chinese\s+trad/si;
  $language = "en_nl" if $msglanguage =~ /dutch/si;
  $language = "en_fr" if $msglanguage =~ /french/si;
  $language = "en_de" if $msglanguage =~ /german/si;
  $language = "en_el" if $msglanguage =~ /greek/si;
  $language = "en_it" if $msglanguage =~ /italian/si;
  $language = "en_ja" if $msglanguage =~ /japanese/si;
  $language = "en_ko" if $msglanguage =~ /korean/si;
  $language = "en_pt" if $msglanguage =~ /portuguese/si;
  $language = "en_ru" if $msglanguage =~ /russian/si;
  $language = "en_es" if $msglanguage =~ /spanish/si;
      
  $text = uri_escape( $text );
  my $urlstring = "http://babelfish.yahoo.com/translate_txt?lp=$language\&trtext=$text";
  print "url: '$urlstring'\n";

  my $request = HTTP::Request->new(GET => $urlstring);
  my $response = $ua->request($request);
  #my $transtext = $response->content();
  my $transtext = $response->decoded_content((charset => 'UTF-8'));
  print $transtext;
  my $output = "fail";
  if ( $transtext =~ /<div id="result"><div style="padding:0.6em;">(.*?)<\/div>/) { 
    $output = $1;
    # utf8::upgrade($output);
  }

  return $output;
}

