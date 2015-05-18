#!/bin/sh
TARGET=handout.md
if [ ! -d "qr" ]; then
  mkdir "qr"
fi
echo '---
classoption: a4paper,10pt
header-includes:
  - \\renewcommand{\\familydefault}{\\sfdefault}
  - \\renewcommand{\\UrlFont}{\\footnotesize}
  - \usepackage{array}
  - \\newcolumntype{V}{>{\\centering\\arraybackslash} m{.4\\linewidth} }
  - \usepackage{graphicx}
  - \usepackage{setspace}
  - \\doublespacing
  - \\pagenumbering{gobble}
---
# sonic gray goo' > "$TARGET"
cat handout.txt >> "$TARGET"
echo '\\vspace{3.5in}\n\n\small{feel free to take any of the reading material with you\n\n
get a goo for your own home:} https://github.com/kevinstadler/sonicgraygoo
\\newpage\n\n# further reading
\\singlespacing
\\begin{tabular}{m{4.5in}V}' >> "$TARGET"
i=0
while read line; do
  if [ -n "$line" ]; then
    echo "$line"
    case "$line" in
      http*)
        i=$((i+1))
        qrencode -o "qr/$i.eps" --type=EPS "$line"
        epspdf "qr/$i.eps"
        echo '\n\\url{' >> "$TARGET"
        echo "$line} & \\includegraphics[width=1in]{qr/$i.pdf}" >> "$TARGET"
        ;;
      *)
        echo '\\\\\\\\' >> "$TARGET"
        echo "$line" >> "$TARGET"
        ;;
    esac
  fi
done < further-reading.txt
echo '\\end{tabular}' >> "$TARGET"
Rscript -e "rmarkdown::render('$TARGET', rmarkdown::pdf_document(fig_width=2, fig_height=2))"

