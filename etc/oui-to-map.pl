#!/usr/bin/perl -w
# data files from https://regauth.standards.ieee.org/standards-ra-web/pub/view.html#registries
use IO::Zlib;
use Text::CSV_XS qw( csv );

open FILER, "> ../wiglewifiwardriving/src/main/assets/oui.properties";
binmode FILER, ":utf8";

my $csv = Text::CSV_XS->new ({ binary => 1, auto_diag => 1 });

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
        print FILER "$key=$val\n";
    }
  }
  close $fh;
}

close FILER;
