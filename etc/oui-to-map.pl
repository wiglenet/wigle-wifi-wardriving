#!/usr/bin/perl -w
# data files from:
# https://code.wireshark.org/review/gitweb?p=wireshark.git;a=blob_plain;f=manuf
# https://regauth.standards.ieee.org/standards-ra-web/pub/view.html#registries
use utf8;
use IO::Zlib;
use Encode qw(decode);
use Text::CSV_XS qw( csv );
binmode(STDOUT, ":utf8");

my $csv = Text::CSV_XS->new ({ binary => 1, auto_diag => 1 });

my %output = ();

for my $file (('oui.csv.gz','mam.csv.gz','oui36.csv.gz')) {
  print "file: $file\n";
  my %header = ();
  $fh = IO::Zlib->new($file, "rb");
  while (my $row = $csv->getline($fh)) {
    my @cols = @$row;
    if (not %header) {
        my $i = 0;
        for $col (@cols) {
             $header{$col} = $i;
             $i++;
        }
    }
    else {
        my $key = $cols[$header{'Assignment'}];
        my $val = $cols[$header{'Organization Name'}];
        $val =~ s/"/\\"/g;
        $val =~ s/\s+$//;
        $output{$key} = $val;
    }
  }
  close $fh;
}

my $file = "wireshark_manuf.gz";
print "file: $file\n";
$fh = IO::Zlib->new($file, "rb");
while(<$fh>) {
  my $line = $_;
  $line = decode("utf8", $line);
  # remove comments
  $line =~ s/#.*$//si;
  $line =~ s/\s+$//sgi;
  next if $line =~ /^\s*$/si;
  # print "line: $line\n";
  if ( $line =~ /^([\w:\/]+)\t(.*?)(?:\t\s*(.*?))?\s*$/si ) {
    my $key = $1;
    my $val = $2;
    my $vallong = $3;
    $key =~ s/(.{10}).*\/28/$1/si;
    $key =~ s/(.{13}).*\/36/$1/si;
    $key =~ s/://sgi;
    $val = $vallong if (defined $vallong and $vallong);
    $val =~ s/Officially Xerox, but 0:0:0:0:0:0 is more common/Xerox Corporation/si;
    # print "oct: $key val: $val\n";
    $output{$key} = $val;
  }
  else {
    print "skipline: $line\n";
  }
}
close $fh;

my $out;
open $out, "> ../wiglewifiwardriving/src/main/assets/oui.properties";
binmode ($out, ":utf8");
foreach my $key (sort keys %output) {
  my $val = $output{$key};
  print $out "$key=$val\n";
}
close $out;
