package pages

object UseCase52 extends BasePage with base.JsWrapper:
  def name = PageNameTyp("UseCase52") 
  
  def render(param: String = ""): Boolean = 
    setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>${name}</h5></div>""")




